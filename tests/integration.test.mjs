import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {Client, root, pinValues} from './client.mjs';

test('real Logisim: hierarchy, editing, timing, batching and persistence', {timeout: 120000}, async () => {
  assert.ok(process.env.LOGISIM_JAR, 'Set LOGISIM_JAR and run npm run build first.');
  const client = new Client();
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'logisim-mcp-test-'));
  const set = (label, value) => client.call('set_input', {label, value: String(value)});
  try {
    await client.call('open_project', {path: path.join(root, 'examples/half-adder.circ')});
    const component = await client.call('get_component', {label: 'U1'});
    assert.deepEqual(component.ports.map(p => p.name), ['a', 'b', 'sum', 'carry']);
    for (const a of [0, 1]) for (const b of [0, 1]) {
      await set('a', a);await set('b', b);
      const parent = pinValues(await client.call('read_signals'));
      const child = pinValues(await client.call('read_signals', {instancePath: ['U1']}));
      assert.equal(parent.sum, String(a ^ b));assert.equal(parent.carry, String(a & b));assert.deepEqual(child, parent);
    }
    const inspected = await client.call('inspect', {instancePath: ['U1']});
    assert.equal(inspected.circuit, 'half_adder');assert.equal((await client.call('status')).circuit, 'main');
    const byCoordinatePath = await client.call('inspect', {instancePath: [component.id]});
    assert.equal(byCoordinatePath.circuit, 'half_adder');
    const xor = await client.call('get_component', {x: 300, y: 140, instancePath: ['U1']});
    assert.equal(xor.type, 'XOR Gate');assert.equal(xor.ports[0].value.hex, '0');
    assert.equal((await client.call('check', {instancePath: ['U1']})).ok, true);
    const saved = path.join(temporary, 'saved.circ');
    await client.call('save_project', {path: saved});
    assert.equal((await client.tool('save_project', {path: saved})).isError, true);
    await client.call('save_project', {path: saved, overwrite: true});
    assert.ok(fs.readdirSync(temporary).some(name => name.startsWith('saved.circ.bak-')));
    await client.call('open_project', {path: saved});
    await set('a', '0b1');await set('b', '0x1');assert.equal(pinValues(await client.call('read_signals')).carry, '1');
    assert.equal((await client.tool('set_input', {label: 'a', value: '2'})).isError, true);
    assert.equal((await client.tool('set_input', {label: 'sum', value: '0'})).isError, true);

    // Invalid path leaves wiring unchanged; valid path is one undo action.
    const before = (await client.call('inspect')).wires.length;
    assert.equal((await client.tool('wire_path', {points: [{x: 800, y: 800}, {x: 900, y: 800}, {x: 910, y: 810}]})).isError, true);
    assert.equal((await client.call('inspect')).wires.length, before);
    await client.call('wire_path', {points: [{x: 800, y: 800}, {x: 900, y: 800}, {x: 900, y: 900}]});
    assert.equal((await client.call('inspect')).wires.length, before + 2);
    await client.call('undo');assert.equal((await client.call('inspect')).wires.length, before);

    // Batch reports a runtime partial failure without undoing the preceding input.
    const partial = await client.tool('batch', {operations: [
      {tool: 'logisim_set_input', arguments: {label: 'a', value: '0'}},
      {tool: 'logisim_set_input', arguments: {label: 'missing', value: '1'}},
      {tool: 'logisim_set_input', arguments: {label: 'a', value: '1'}},
    ]});
    assert.equal(partial.isError, true);assert.equal(partial.structuredContent.result.completed, 1);assert.equal(partial.structuredContent.result.failedIndex, 1);
    assert.equal(pinValues(await client.call('read_signals')).a, '0');
    await client.call('save_project', {path: saved, overwrite: true});

    await client.call('open_project', {path: path.join(root, 'examples/d-flip-flop.circ')});
    assert.deepEqual((await client.call('get_component', {label: 'state'})).ports.map(p => p.name), ['D', 'clock', 'Q', 'notQ', 'clear', 'preset']);
    await set('CP', 0);await set('RESET', 1);assert.equal(pinValues(await client.call('read_signals')).Q, '0');
    await set('RESET', 0);await set('D', 1);assert.equal(pinValues(await client.call('read_signals')).Q, '0');
    await client.call('tick', {count: 2});assert.equal(pinValues(await client.call('read_signals')).Q, '0');
    await set('CP', 1);assert.equal(pinValues(await client.call('read_signals')).Q, '1');
    await set('D', 0);assert.equal(pinValues(await client.call('read_signals')).Q, '1');
    await set('CP', 0);await set('CP', 1);assert.equal(pinValues(await client.call('read_signals')).Q, '0');
    await set('D', 1);await set('CP', 0);await set('CP', 1);await set('RESET', 1);
    assert.equal(pinValues(await client.call('read_signals')).Q, '0');

    // Wider inputs and explicit coordinates continue to work.
    await client.call('new_project');
    await client.call('add_component', {type: 'Pin', x: 100, y: 100, attributes: {label: 'word', width: '8'}});
    for (const value of ['0xF5', '0b10100101', '255']) {
      const read = pinValues(await client.call('set_input', {x: 100, y: 100, value}));
      assert.equal(BigInt(`0x${read.word}`), BigInt(value));
    }
    assert.equal((await client.tool('set_input', {label: 'word', value: '256'})).isError, true);
    assert.equal((await client.tool('new_project')).isError, true);
    await client.call('save_project', {path: path.join(temporary, 'word.circ')});

    // A new built-in Clock really advances through half-cycle ticks.
    await client.call('new_project');
    const clock = await client.call('add_component', {type: 'Clock', x: 100, y: 100});
    const output = await client.call('add_component', {type: 'Pin', x: 300, y: 100, attributes: {label: 'out', output: 'true', facing: 'west'}});
    await client.call('wire_path', {points: [clock.ports[0].location, output.ports[0].location]});
    await client.call('tick', {count: 1});assert.equal(pinValues(await client.call('read_signals')).out, '1');
    await client.call('tick', {count: 1});assert.equal(pinValues(await client.call('read_signals')).out, '0');
    await client.call('save_project', {path: path.join(temporary, 'clock.circ')});

    // An actual two-driver conflict is reported as an electrical error.
    await client.call('new_project');
    await client.call('add_component', {type: 'Pin', x: 100, y: 100, attributes: {label: 'L'}});
    await client.call('add_component', {type: 'Pin', x: 300, y: 100, attributes: {label: 'R'}});
    await client.call('wire', {x1: 100, y1: 100, x2: 300, y2: 100});
    await set('L', 0);await set('R', 1);
    const conflict = await client.call('check');
    assert.equal(conflict.ok, false);assert.ok(conflict.errorPorts.length >= 2);
    await client.call('save_project', {path: path.join(temporary, 'conflict.circ')});

    // Recovery data must not trigger an invisible blocking prompt or be deleted.
    const recovery = path.join(temporary, '.clock.circ.autosave');fs.writeFileSync(recovery, 'synthetic recovery marker');
    const failed = await client.tool('open_project', {path: path.join(temporary, 'clock.circ')});
    assert.equal(failed.isError, true);assert.match(failed.structuredContent.result.error, /recovery/i);
    assert.equal(fs.readFileSync(recovery, 'utf8'), 'synthetic recovery marker');
  } finally {await client.close();fs.rmSync(temporary, {recursive: true, force: true});}
});
