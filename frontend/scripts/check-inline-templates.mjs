import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseTemplate } from '@angular/compiler';

const sourceRoot = fileURLToPath(new URL('../src/', import.meta.url));
const files = [];

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    const st = statSync(path);
    if (st.isDirectory()) walk(path);
    else if (name.endsWith('.ts')) files.push(path);
  }
}

walk(sourceRoot);
let checked = 0;
let failures = 0;

for (const path of files) {
  const text = readFileSync(path, 'utf8');
  const inlineTemplates = /template\s*:\s*`([\s\S]*?)`/g;
  for (const match of text.matchAll(inlineTemplates)) {
    checked++;
    const file = relative(sourceRoot, path).replaceAll('\\', '/');
    const result = parseTemplate(match[1], file, { preserveWhitespaces: false });
    for (const error of result.errors ?? []) {
      failures++;
      console.error(`[Angular template] ${file}: ${error.toString()}`);
    }
  }
}

if (failures > 0) {
  console.error(`Validación Angular: ${failures} error(es) en ${checked} plantilla(s) inline.`);
  process.exit(1);
}

console.log(`Validación Angular OK: ${checked} plantilla(s) inline.`);
