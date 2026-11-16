import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.proj.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.*;
import com.cburch.logisim.util.StringUtil;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Local in-process adapter for the installed Logisim-evolution 3.9.0 APIs. */
public class LogisimBridge {
  static Project project;
  static boolean headless = Boolean.parseBoolean(System.getenv("LOGISIM_HEADLESS"));
  static PrintStream protocol = System.out;
  static Map<String,Object> obj(Object... pairs) {
    Map<String,Object> m=new LinkedHashMap<>();
    for(int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]);
    return m;
  }
  static String json(Object v) {
    if(v==null) return "null";
    if(v instanceof Boolean || v instanceof Number) return v.toString();
    if(v instanceof Map<?,?> m) {
      List<String> a=new ArrayList<>(); m.forEach((k,x)->a.add(json(k.toString())+":"+json(x)));
      return "{"+String.join(",",a)+"}";
    }
    if(v instanceof Collection<?> c) { List<String>a=new ArrayList<>(); for(Object x:c)a.add(json(x));return "["+String.join(",",a)+"]"; }
    StringBuilder s=new StringBuilder("\"");
    for(char c:v.toString().toCharArray()) switch(c) {
      case '"':s.append("\\\"");break; case '\\':s.append("\\\\");break;
      case '\n':s.append("\\n");break; case '\r':s.append("\\r");break; case '\t':s.append("\\t");break;
      default: if(c<32)s.append(String.format("\\u%04x",(int)c));else s.append(c);
    }
    return s.append('"').toString();
  }
  static Circuit circuit(){if(project==null)throw new IllegalStateException("Open or create a project first");return project.getCurrentCircuit();}
  static CircuitState state(){circuit();return project.getCircuitState();}
  static void dirty(){project.setFileAsDirty();project.repaintCanvas();}
  static void settle(){settle(true);}
  static void settle(boolean failOnOscillation){state().getPropagator().propagate();for(Component c:circuit().getNonWires())if(c.getFactory() instanceof Pin p&&p.isInputPin(Instance.getInstanceFor(c)))p.propagate(state().getInstanceState(c));state().getPropagator().propagate();project.repaintCanvas();if(failOnOscillation&&state().getPropagator().isOscillating())throw new IllegalStateException("Circuit oscillates; use logisim_check to inspect it");}
  static Location loc(String x,String y){int a=Integer.parseInt(x),b=Integer.parseInt(y);if(a<0||b<0||a>10000||b>10000||a%10!=0||b%10!=0)throw new IllegalArgumentException("Coordinates must be 0..10000 on 10-unit grid");return Location.create(a,b,true);}
  static Map<String,Object> xy(Location l){return obj("x",l.getX(),"y",l.getY());}
  @SuppressWarnings({"rawtypes","unchecked"})
  static Map<String,Object> attrs(AttributeSet set){Map<String,Object> m=new LinkedHashMap<>();for(Attribute a:set.getAttributes())try{m.put(a.getName(),a.toStandardString(set.getValue(a)));}catch(Exception ignored){}return m;}
  static String label(Component c) {
    try {String value=c.getAttributeSet().getValue(StdAttr.LABEL);return value==null?"":value;}catch(Exception e){return "";}
  }
  static Map<String,Object> value(Value v){return obj("binary",v.toBinaryString(),"hex",v.toHexString(),"defined",v.isFullyDefined(),"error",v.isErrorValue());}
  static String portName(Component c,int index) {
    String type=c.getFactory().getName();
    if(type.equals("D Flip-Flop"))return new String[]{"D","clock","Q","notQ","clear","preset"}[index];
    if(type.equals("Register"))return new String[]{"Q","D","clock","clear","enable"}[index];
    if(type.equals("Adder")||type.equals("Subtractor"))return new String[]{"a","b","result","carry_in","carry_out"}[index];
    Instance instance=Instance.getInstanceFor(c);
    if(instance!=null&&index<instance.getPorts().size()) {
      String tip=instance.getPorts().get(index).getToolTip();
      if(tip!=null&&!tip.isBlank())return tip;
    }
    if(type.endsWith(" Gate"))return index==0?"output":"input"+index;
    if(type.equals("Splitter"))return index==0?"bus":"branch"+(index-1);
    return "port"+index;
  }
  static Map<String,Object> describe(Component c){return describe(c,state());}
  static Map<String,Object> describe(Component c,CircuitState state){
    List<Object> ends=new ArrayList<>();int i=0;
    for(EndData e:c.getEnds())ends.add(obj("port",i,"name",portName(c,i++),"location",xy(e.getLocation()),"width",e.getWidth().getWidth(),"input",e.isInput(),"output",e.isOutput(),"value",value(state.getValue(e.getLocation()))));
    return obj("id","@"+c.getLocation().getX()+","+c.getLocation().getY(),"type",c.getFactory().getName(),"label",label(c),"location",xy(c.getLocation()),"attributes",attrs(c.getAttributeSet()),"ports",ends);
  }
  static Component at(String x,String y){Location l=loc(x,y);List<Component>a=new ArrayList<>();for(Component c:circuit().getNonWires())if(c.getLocation().equals(l))a.add(c);if(a.size()!=1)throw new IllegalArgumentException("Expected exactly one component at "+l+", found "+a.size());return a.get(0);}
  static Component selected(Circuit circuit,String selector) {
    List<Component> matches=new ArrayList<>();
    if(selector.startsWith("@")) {
      String[] p=selector.substring(1).split(",",-1);
      if(p.length!=2)throw new IllegalArgumentException("Coordinate selector must be @x,y");
      Location location=loc(p[0],p[1]);
      for(Component c:circuit.getNonWires())if(c.getLocation().equals(location))matches.add(c);
    } else {
      String wanted=selector.startsWith("label:")?selector.substring(6):selector;
      for(Component c:circuit.getNonWires())if(label(c).equals(wanted))matches.add(c);
    }
    if(matches.size()!=1)throw new IllegalArgumentException("Selector matched "+matches.size()+" components; use a unique label or an exact @x,y selector");
    return matches.get(0);
  }
  record Context(Circuit circuit,CircuitState state,List<String> path){}
  static Context context(String[] args,int start) {return context(args,start,true);}
  static Context context(String[] args,int start,boolean failOnOscillation) {
    settle(failOnOscillation);CircuitState s=state();List<String> path=new ArrayList<>();
    for(int i=start;i<args.length;i++) {
      Component c=selected(s.getCircuit(),args[i]);
      if(!(c.getFactory() instanceof SubcircuitFactory factory))throw new IllegalArgumentException("Instance path must contain subcircuit components only");
      s=factory.getSubstate(s,c);path.add(args[i]);
    }
    return new Context(s.getCircuit(),s,path);
  }
  static void collect(Library lib,Map<String,ComponentFactory> all){for(Tool t:lib.getTools())if(t instanceof AddTool a)all.putIfAbsent(a.getFactory().getName(),a.getFactory());for(Library sub:lib.getLibraries())collect(sub,all);}
  static Map<String,ComponentFactory> factories(){Map<String,ComponentFactory> all=new TreeMap<>();if(project!=null)collect(project.getLogisimFile(),all);else collect(new com.cburch.logisim.std.Builtin(),all);return all;}
  static ComponentFactory factory(String name){ComponentFactory f=factories().get(name);if(f==null)throw new IllegalArgumentException("Unknown component "+name);return f;}
  static void install(LogisimFile f){
    if(project!=null&&project.getFrame()!=null)project.getFrame().dispose();
    project=new Project(f);for(Circuit c:f.getCircuits())c.setProject(project);project.getSimulator().setAutoPropagation(false);project.getSimulator().setAutoTicking(false);
    if(!headless){var frame=new com.cburch.logisim.gui.main.Frame(project);project.setFrame(frame);frame.setSize(1320,820);frame.setVisible(true);}
  }
  static void requireClean(){if(project!=null&&project.isFileDirty())throw new IllegalStateException("Save unsaved changes before replacing project");}
  static Object info(){List<Object> cs=new ArrayList<>();for(Circuit c:project.getLogisimFile().getCircuits())cs.add(c.getName());return obj("circuit",circuit().getName(),"circuits",cs,"dirty",project.isFileDirty(),"gui",!headless,"oscillating",state().getPropagator().isOscillating());}
  static Object signals(){settle();List<Object> a=new ArrayList<>();for(Component c:circuit().getNonWires())if(c.getFactory() instanceof Pin p){Instance i=Instance.getInstanceFor(c);Value v=p.getValue(state().getInstanceState(c));a.add(obj("label",c.getAttributeSet().getValue(StdAttr.LABEL),"location",xy(c.getLocation()),"input",p.isInputPin(i),"binary",v.toBinaryString(),"hex",v.toHexString(),"defined",v.isFullyDefined()));}return a;}
  static Object signals(Context context) {
    List<Object> result=new ArrayList<>();
    for(Component c:context.circuit().getNonWires())if(c.getFactory() instanceof Pin pin) {
      Value v=pin.getValue(context.state().getInstanceState(c));
      result.add(obj("label",label(c),"location",xy(c.getLocation()),"input",pin.isInputPin(Instance.getInstanceFor(c)),"binary",v.toBinaryString(),"hex",v.toHexString(),"defined",v.isFullyDefined(),"error",v.isErrorValue()));
    }
    return result;
  }
  @SuppressWarnings({"rawtypes","unchecked"})
  static Object run(String[] a)throws Exception {
    switch(a[0]) {
      case "status":return project==null?obj("project",null,"gui",!headless):info();
      case "new": requireClean();install(ProjectActions.createNewFile(null));return info();
      case "open":{
        requireClean();File file=new File(a[1]);
        if(!file.isFile())throw new IOException("Circuit file not found");
        File recovery=new File(file.getParentFile(),"."+file.getName()+".autosave");
        if(recovery.exists())throw new IOException("Autosave recovery file detected. Resolve recovery in Logisim, or copy the saved project and its relative dependencies to a clean directory before opening it. The MCP will not delete recovery data.");
        install(new Loader(null).openLogisimFile(file));return info();
      }
      case "save":{circuit();File f=new File(a[1]);if(f.exists()&&!Boolean.parseBoolean(a[2]))throw new IllegalArgumentException("Target exists; pass overwrite=true");if(f.exists())Files.copy(f.toPath(),Path.of(f+".bak-"+System.currentTimeMillis()));if(!ProjectActions.doSave(project,f))throw new IOException("Save failed");return obj("path",f.getAbsolutePath());}
      case "catalog":if(a.length>1&&!a[1].isEmpty())return obj("type",a[1],"attributes",attrs(factory(a[1]).createAttributeSet()));return factories().keySet();
      case "inspect":{Context ctx=context(a,1);List<Object>cs=new ArrayList<>(),ws=new ArrayList<>();for(Component c:ctx.circuit().getNonWires())cs.add(describe(c,ctx.state()));for(Wire w:ctx.circuit().getWires())ws.add(obj("from",xy(w.getEnd0()),"to",xy(w.getEnd1())));return obj("project",info(),"circuit",ctx.circuit().getName(),"instancePath",ctx.path(),"components",cs,"wires",ws);}
      case "component":{Context ctx=context(a,2);return describe(selected(ctx.circuit(),a[1]),ctx.state());}
      case "check":{
        Context ctx=context(a,2,false);List<Object> errors=new ArrayList<>(),unknown=new ArrayList<>();Map<String,Integer> labels=new TreeMap<>();
        for(Component c:ctx.circuit().getNonWires()) {
          if(!label(c).isEmpty())labels.merge(label(c),1,Integer::sum);
          int index=0;
          for(EndData end:c.getEnds()) {
            Value v=ctx.state().getValue(end.getLocation());
            Map<String,Object> issue=obj("type",c.getFactory().getName(),"label",label(c),"location",xy(c.getLocation()),"port",index,"portName",portName(c,index),"portLocation",xy(end.getLocation()),"value",value(v));
            if(v.isErrorValue())errors.add(issue);else if(Boolean.parseBoolean(a[1])&&!v.isFullyDefined())unknown.add(issue);
            index++;
          }
        }
        Map<String,Integer> duplicateLabels=new TreeMap<>();labels.forEach((k,v)->{if(v>1)duplicateLabels.put(k,v);});
        boolean oscillating=state().getPropagator().isOscillating();
        return obj("circuit",ctx.circuit().getName(),"instancePath",ctx.path(),"ok",errors.isEmpty()&&!oscillating,"oscillating",oscillating,"errorPorts",errors,"unknownPorts",unknown,"duplicateLabels",duplicateLabels,"note","Electrical checks only: unknown/unconnected ports may be intentional. Verify logic with expected test vectors.");
      }
      case "circuit":{circuit();Circuit c=project.getLogisimFile().getCircuit(a[1]);if(c==null){if(!Boolean.parseBoolean(a[2]))throw new IllegalArgumentException("Circuit not found");if(!a[1].matches("[A-Za-z_][A-Za-z_0-9]*"))throw new IllegalArgumentException("Use an ASCII identifier for circuit name");c=new Circuit(a[1],project.getLogisimFile(),project);project.getLogisimFile().addCircuit(c);dirty();}project.setCurrentCircuit(c);return info();}
      case "add":{Circuit cir=circuit();ComponentFactory f=factory(a[1]);AttributeSet set=f.createAttributeSet();for(int i=4;i<a.length;i+=2){Attribute attr=set.getAttribute(a[i]);if(attr==null||set.isReadOnly(attr))throw new IllegalArgumentException("Unknown/read-only attribute "+a[i]);Object v=attr.parse(a[i+1]);if(v==null)throw new IllegalArgumentException("Invalid attribute value");set.setValue(attr,v);}Component c=f.createComponent(loc(a[2],a[3]),set);if(cir.hasConflict(c))throw new IllegalArgumentException("Component overlaps another component");CircuitMutation m=new CircuitMutation(cir);m.add(c);project.doAction(m.toAction(StringUtil.constantGetter("MCP add component")));dirty();return describe(c);}
      case "attribute":{Component c=at(a[1],a[2]);Attribute attr=c.getAttributeSet().getAttribute(a[3]);if(attr==null||c.getAttributeSet().isReadOnly(attr))throw new IllegalArgumentException("Unknown/read-only attribute");Object v=attr.parse(a[4]);if(v==null)throw new IllegalArgumentException("Invalid value");CircuitMutation m=new CircuitMutation(circuit());m.set(c,attr,v);project.doAction(m.toAction(StringUtil.constantGetter("MCP edit attribute")));dirty();return describe(c);}
      case "remove":{Component c=at(a[1],a[2]);CircuitMutation m=new CircuitMutation(circuit());m.remove(c);project.doAction(m.toAction(StringUtil.constantGetter("MCP remove component")));dirty();return info();}
      case "wire":{Location p=loc(a[1],a[2]),q=loc(a[3],a[4]);if(p.equals(q)||p.getX()!=q.getX()&&p.getY()!=q.getY())throw new IllegalArgumentException("Wire must be nonzero and horizontal/vertical");Wire w=Wire.create(p,q);CircuitMutation m=new CircuitMutation(circuit());if(a.length>5&&a[5].equals("remove"))m.remove(w);else m.add(w);project.doAction(m.toAction(StringUtil.constantGetter("MCP wire")));dirty();return obj("from",xy(p),"to",xy(q));}
      case "wirepath":{
        if(a.length<6||(a.length-2)%2!=0)throw new IllegalArgumentException("At least two coordinate pairs required");
        List<Wire> wires=new ArrayList<>();
        for(int i=2;i+3<a.length;i+=2) {
          Location from=loc(a[i],a[i+1]),to=loc(a[i+2],a[i+3]);
          if(from.equals(to)||from.getX()!=to.getX()&&from.getY()!=to.getY())throw new IllegalArgumentException("Each path segment must be nonzero and horizontal/vertical");
          wires.add(Wire.create(from,to));
        }
        CircuitMutation mutation=new CircuitMutation(circuit());
        for(Wire wire:wires)if(Boolean.parseBoolean(a[1]))mutation.remove(wire);else mutation.add(wire);
        project.doAction(mutation.toAction(StringUtil.constantGetter("MCP wire path")));dirty();return obj("segments",wires.size());
      }
      case "input":{Component c=selected(circuit(),a[1]);if(!(c.getFactory() instanceof Pin p)||!p.isInputPin(Instance.getInstanceFor(c)))throw new IllegalArgumentException("Target must be an input pin");int width=p.getWidth(Instance.getInstanceFor(c)).getWidth();java.math.BigInteger n=new java.math.BigInteger(a[2]);if(n.signum()<0||n.bitLength()>width)throw new IllegalArgumentException("Value does not fit pin width");p.setValue(state().getInstanceState(c),Value.createKnown(width,n.longValue()));state().markComponentAsDirty(c);return signals();}
      case "tick":{int n=Integer.parseInt(a[1]);if(n<1||n>10000)throw new IllegalArgumentException("Ticks must be 1..10000");settle();for(int i=0;i<n;i++){state().getPropagator().toggleClocks();settle();}return signals();}
      case "signals":return signals(context(a,1));
      case "reset":project.setCircuitState(new CircuitState(project,circuit()));return signals();
      case "undo":project.undoAction();return info();
      case "snapshot":{if(headless)throw new IllegalStateException("GUI required");var canvas=project.getFrame().getCanvas();BufferedImage im=new BufferedImage(Math.max(1,canvas.getWidth()),Math.max(1,canvas.getHeight()),BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();canvas.paint(g);g.dispose();ImageIO.write(im,"png",new File(a[1]));return obj("path",a[1]);}
      default:throw new IllegalArgumentException("Unknown operation: "+a[0]);
    }
  }
  public static void main(String[] args)throws Exception {
    System.setOut(System.err); Main.headless=headless;
    com.cburch.logisim.prefs.AppPreferences.AUTOSAVE_ENABLED.set(false);
    BufferedReader reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));String line;
    while((line=reader.readLine())!=null){String[] a=line.split("\t",-1);for(int i=0;i<a.length;i++)a[i]=new String(Base64.getDecoder().decode(a[i]),StandardCharsets.UTF_8);
      final String[] command=a;final Object[] result=new Object[1];
      SwingUtilities.invokeAndWait(()->{try{result[0]=obj("result",run(command));}catch(Throwable e){e.printStackTrace(System.err);result[0]=obj("error",e.toString());}});
      protocol.println(json(result[0]));protocol.flush();
    }
    System.exit(0);
  }
}
