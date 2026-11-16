import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
import {once} from 'node:events';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export class Client {
  constructor(env = {}) {
    this.id = 0;this.pending = new Map();this.stderr = '';
    this.child = spawn(process.execPath, [path.join(root, 'server/index.mjs')], {
      env: {...process.env, LOGISIM_HEADLESS: 'true', ...env}, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true,
    });
    this.child.stderr.on('data', data => {this.stderr = (this.stderr + data).slice(-8000);});
    createInterface({input: this.child.stdout}).on('line', line => {
      let message;
      try {message = JSON.parse(line);} catch {this.fail(Error('Non-JSON stdout from MCP server'));return;}
      const pending = this.pending.get(message.id);
      if (pending) {this.pending.delete(message.id);clearTimeout(pending.timer);pending.resolve(message);}
    });
    this.child.on('error', error => this.fail(error));
    this.child.on('exit', code => this.fail(Error(`MCP process exited: ${code}`)));
  }
  fail(error) {for (const p of this.pending.values()) {clearTimeout(p.timer);p.reject(error);}this.pending.clear();}
  async request(method, params = {}) {
    const message = await this.raw({jsonrpc: '2.0', id: ++this.id, method, params});
    if (message.error) throw Error(JSON.stringify(message.error));
    return message.result;
  }
  raw(message) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {this.pending.delete(message.id);reject(Error('MCP request timeout'));}, 70000);
      this.pending.set(message.id, {resolve, reject, timer});
      this.child.stdin.write(JSON.stringify(message) + '\n');
    });
  }
  async tool(name, args = {}) {return this.request('tools/call', {name: `logisim_${name}`, arguments: args});}
  async call(name, args = {}) {
    const result = await this.tool(name, args);
    const value = result.structuredContent?.result ?? JSON.parse(result.content[0].text);
    if (result.isError) throw Error(value.error ?? JSON.stringify(value));
    return value;
  }
  async close() {
    if (this.child.exitCode !== null) return;
    const exited = once(this.child, 'exit');
    this.child.stdin.end();
    const timer = setTimeout(() => this.child.kill(), 5000);
    try {await exited;} finally {clearTimeout(timer);}
  }
}
export function pinValues(pins) {return Object.fromEntries(pins.map(pin => [pin.label, pin.hex.replaceAll(' ', '')]));}
