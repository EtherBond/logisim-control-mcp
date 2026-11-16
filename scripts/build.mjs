import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const jar = process.env.LOGISIM_JAR;
if (!jar || !path.isAbsolute(jar) || !fs.statSync(jar, {throwIfNoEntry: false})?.isFile()) {
  throw Error('Set LOGISIM_JAR to an existing absolute logisim-evolution-3.9.0-all.jar path.');
}
const javac = process.env.LOGISIM_JAVAC || 'javac';
const version = spawnSync(javac, ['-version'], {encoding: 'utf8', windowsHide: true});
const major = Number(`${version.stdout ?? ''} ${version.stderr ?? ''}`.match(/javac\s+(\d+)/)?.[1]);
if (version.error || version.status !== 0 || !(major >= 21)) throw Error('A JDK 21+ javac is required. Set LOGISIM_JAVAC if it is not on PATH.');
fs.mkdirSync(path.join(root, 'build'), {recursive: true});
const result = spawnSync(javac, [
  '--release', '21', '-encoding', 'UTF-8', '-classpath', jar, '-d', path.join(root, 'build'),
  path.join(root, 'java', 'LogisimBridge.java'), path.join(root, 'java', 'LocalPreferences.java'),
], {stdio: 'inherit', windowsHide: true});
if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);
process.stdout.write('Built Java bridge for Java 21+. No external JAR was copied into this repository.\n');
