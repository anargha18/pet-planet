// Frontend folder-boundary rules (R-STR-05). Run: npm run check:boundaries
//
//  1. A feature imports another feature only through its public index ('../cart'), never its files ('../cart/CartPage').
//  2. Backend calls go through the api index ('../../api'), never individual api modules ('../../api/orders').
//  3. Features never import from app/ (the app composes features, not the other way round).
//  4. Shared components/ may use a feature's public index only.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const srcDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src');
const featuresDir = join(srcDir, 'features');
const apiDir = join(srcDir, 'api');
const appDir = join(srcDir, 'app');

const walk = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? walk(path) : /\.(jsx?|mjs)$/.test(name) ? [path] : [];
  });

const featureOf = (path) => {
  const rel = relative(featuresDir, path);
  return rel.startsWith('..') ? null : rel.split(sep)[0];
};

const violations = [];
for (const file of walk(srcDir)) {
  const source = readFileSync(file, 'utf8');
  for (const match of source.matchAll(/from\s+['"](\.{1,2}\/[^'"]+)['"]/g)) {
    const target = resolve(dirname(file), match[1]);
    const where = `${relative(srcDir, file)} -> '${match[1]}'`;
    const fromFeature = featureOf(file);
    const toFeature = featureOf(target);

    if (toFeature && toFeature !== fromFeature && target !== join(featuresDir, toFeature)) {
      violations.push(`${where}: import another feature via its index ('.../${toFeature}')`);
    }
    if (!file.startsWith(apiDir + sep) && target.startsWith(apiDir + sep)) {
      violations.push(`${where}: import backend calls from the api index, not an api module`);
    }
    if (fromFeature && (target === appDir || target.startsWith(appDir + sep))) {
      violations.push(`${where}: features must not import from app/`);
    }
  }
}

if (violations.length) {
  console.error(`Frontend boundary violations (${violations.length}):\n  ${violations.join('\n  ')}`);
  process.exit(1);
}
console.log('Frontend boundaries OK');
