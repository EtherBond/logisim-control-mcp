import java.util.*;
import java.util.prefs.*;
/** Isolated preferences: never reads or changes the user's Logisim registry settings. */
public final class LocalPreferences implements PreferencesFactory {
  private static final Preferences ROOT=new Memory(null, "");
  public Preferences userRoot(){return ROOT;}
  public Preferences systemRoot(){return ROOT;}
  private static final class Memory extends AbstractPreferences {
    final Map<String,String> values=new HashMap<>();
    final Map<String,Memory> children=new HashMap<>();
    Memory(AbstractPreferences parent,String name){super(parent,name);}
    protected void putSpi(String k,String v){values.put(k,v);}
    protected String getSpi(String k){return values.get(k);}
    protected void removeSpi(String k){values.remove(k);}
    protected void removeNodeSpi(){values.clear();children.clear();}
    protected String[] keysSpi(){return values.keySet().toArray(String[]::new);}
    protected String[] childrenNamesSpi(){return children.keySet().toArray(String[]::new);}
    protected AbstractPreferences childSpi(String name){return children.computeIfAbsent(name,n->new Memory(this,n));}
    protected void syncSpi(){}
    protected void flushSpi(){}
  }
}
