import path from 'node:path';
import {Client, root} from '../tests/client.mjs';

const client = new Client();
const add = (type, x, y, attributes = {}) => client.call('add_component', {type, x, y, attributes});
const pin = (label, x, y, output = false) => add('Pin', x, y, {label, output: String(output), facing: output ? 'west' : 'east', appearance: 'classic'});
const wire = (points) => client.call('wire_path', {points: points.filter((p, i) => i === 0 || p[0] !== points[i-1][0] || p[1] !== points[i-1][1]).map(([x, y]) => ({x, y}))});
const port = (part, name) => {
  const found = part.ports.find(p => p.name === name);
  if (!found) throw Error(`Missing port ${name}`);
  return [found.location.x, found.location.y];
};
try {
  await client.call('new_project');
  await client.call('select_circuit', {name: 'half_adder', create: true});
  await pin('a', 100, 100);await pin('b', 100, 200);
  const xor = await add('XOR Gate', 300, 140);
  const and = await add('AND Gate', 300, 300);
  await pin('sum', 500, 140, true);await pin('carry', 500, 300, true);
  await wire([[100, 100], [200, 100], [200, 120], port(xor, 'input1')]);
  await wire([[200, 120], [200, 280], port(and, 'input1')]);
  await wire([[100, 200], [180, 200], [180, 160], port(xor, 'input2')]);
  await wire([[180, 200], [180, 320], port(and, 'input2')]);
  await wire([port(xor, 'output'), [500, 140]]);await wire([port(and, 'output'), [500, 300]]);
  await client.call('select_circuit', {name: 'main'});
  await pin('a', 100, 100);await pin('b', 100, 200);
  const sub = await add('half_adder', 400, 140, {label: 'U1'});
  await pin('sum', 600, 140, true);await pin('carry', 600, 300, true);
  const [ax, ay] = port(sub, 'a'), [bx, by] = port(sub, 'b');
  await wire([[100, 100], [180, 100], [180, ay], [ax, ay]]);
  await wire([[100, 200], [200, 200], [200, by], [bx, by]]);
  const [sx, sy] = port(sub, 'sum');await wire([[sx, sy], [600, sy], [600, 140]]);
  const [cx, cy] = port(sub, 'carry');await wire([[cx, cy], [450, cy], [450, 300], [600, 300]]);
  await client.call('save_project', {path: path.join(root, 'examples/half-adder.circ'), overwrite: true});

  await client.call('new_project');
  await pin('D', 100, 100);await pin('CP', 100, 200);await pin('RESET', 100, 300);
  const ff = await add('D Flip-Flop', 350, 100, {label: 'state'});
  await pin('Q', 600, 100, true);
  const d = port(ff, 'D'), cp = port(ff, 'clock'), clear = port(ff, 'clear'), q = port(ff, 'Q'), preset = port(ff, 'preset');
  await wire([[100, 100], [200, 100], [200, d[1]], d]);
  await wire([[100, 200], [240, 200], [240, cp[1]], cp]);
  await wire([[100, 300], [280, 300], [280, clear[1]], clear]);
  await wire([q, [500, q[1]], [500, 100], [600, 100]]);
  const ground = await add('Ground', preset[0], 50);
  await wire([[ground.ports[0].location.x, ground.ports[0].location.y], preset]);
  await client.call('save_project', {path: path.join(root, 'examples/d-flip-flop.circ'), overwrite: true});
  console.log('Generated two synthetic examples through the MCP API.');
} finally {await client.close();}
