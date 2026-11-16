import test from 'node:test';
import assert from 'node:assert/strict';
import {handle, definitions, validate} from '../server/index.mjs';
import {Client} from './client.mjs';

const request = (method, params = {}) => handle({jsonrpc: '2.0', id: 1, method, params});
const call = (name, args = {}) => request('tools/call', {name: `logisim_${name}`, arguments: args});
test('discovery, protocol versions and named documentation resources', async () => {
  const init = await request('initialize', {protocolVersion: '2025-11-25'});
  assert.equal(init.result.serverInfo.version, '1.1.0');
  assert.deepEqual(init.result.capabilities, {tools: {}, resources: {}});
  assert.match(init.result.instructions, /instancePath/);
  assert.equal((await request('tools/list')).result.tools.length, 22);
  for (const version of ['2024-11-05', '2025-03-26', '2025-06-18', '2025-11-25']) assert.equal((await request('initialize', {protocolVersion: version})).result.protocolVersion, version);
  assert.equal((await request('initialize', {protocolVersion: 'unknown'})).result.protocolVersion, '2025-11-25');
  const resources = (await request('resources/list')).result.resources;
  assert.equal(resources.length, 4);
  for (const resource of resources) {
    const contents = (await request('resources/read', {uri: resource.uri})).result.contents;
    assert.ok(contents[0].text.length > 100);
    assert.equal(contents[0].uri, resource.uri);
  }
  const help = (await call('help')).result;
  assert.equal(help.isError, false);
  assert.deepEqual(JSON.parse(help.content[0].text), help.structuredContent.result);
});
test('invalid schemas and selectors fail without starting Java', async () => {
  for (const [name, args] of [
    ['tick', {count: 0}], ['tick', {count: 1.5}], ['tick', {count: 10001}],
    ['add_component', {type: 'Pin', x: 101, y: 100}],
    ['set_input', {label: 'a', x: 100, y: 100, value: '1'}],
    ['set_input', {value: '1'}], ['set_input', {label: 'a', value: '-1'}],
    ['set_input', {label: 'a', value: '0xGG'}],
    ['inspect', {instancePath: 'child'}], ['help', {topic: '../SECURITY'}],
    ['status', {unexpected: 1}], ['wire_path', {points: [{x: 100, y: 100}]}],
  ]) assert.equal((await call(name, args)).result.isError, true, name);
  const batch = await call('batch', {operations: [
    {tool: 'logisim_status'}, {tool: 'logisim_tick', arguments: {count: 0}},
  ]});
  assert.match(batch.result.structuredContent.result.error, /integer/);
  assert.equal(batch.result.structuredContent.result.completed, undefined);
  for (const tool of ['logisim_batch', 'logisim_save_project']) assert.equal((await call('batch', {operations: [{tool}]})).result.isError, true);
});
test('protocol errors, read-only hints and guard against resource traversal', async () => {
  assert.equal((await handle([])).error.code, -32600);
  assert.equal(await handle({jsonrpc: '2.0', method: 'notifications/initialized'}), undefined);
  assert.equal((await request('unsupported')).error.code, -32601);
  assert.equal((await request('resources/read', {uri: 'file:///etc/passwd'})).error.code, -32602);
  assert.equal(definitions.find(d => d.spec.name === 'logisim_snapshot').spec.annotations.readOnlyHint, false);
  assert.equal(definitions.find(d => d.spec.name === 'logisim_inspect').spec.annotations.readOnlyHint, true);
  assert.throws(() => validate({type: 'object', properties: {}, additionalProperties: false}, []));
});
test('real stdio round trip, no JAR configuration needed for documentation', async () => {
  const client = new Client({LOGISIM_JAR: ''});
  try {
    assert.equal((await client.request('initialize')).serverInfo.name, 'logisim-control');
    assert.equal((await client.call('help', {topic: 'recipes'})).topic, 'recipes');
    const failed = await client.tool('status');
    assert.equal(failed.isError, true);
    assert.match(failed.structuredContent.result.error, /LOGISIM_JAR/);
  } finally {await client.close();}
});
