import {registerHooks} from 'node:module';
import {readFileSync, existsSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import ts from 'typescript';

const root = new URL('../../src/', import.meta.url);
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier.startsWith('@/') || (specifier.startsWith('.') && context.parentURL?.startsWith(root.href))) {
      const base = specifier.startsWith('@/')
        ? new URL(specifier.slice(2), root)
        : new URL(specifier, context.parentURL);
      for (const suffix of ['', '.ts', '/index.ts']) {
        const url = `${base.href}${suffix}`;
        if (url.endsWith('.ts') && existsSync(fileURLToPath(url))) {
          return {url, shortCircuit: true};
        }
      }
    }
    return nextResolve(specifier, context);
  },
  load(url, context, nextLoad) {
    if (url.startsWith(root.href) && url.endsWith('.ts')) {
      return {
        format: 'module',
        shortCircuit: true,
        source: ts.transpileModule(readFileSync(new URL(url), 'utf8'), {
          compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022},
        }).outputText,
      };
    }
    return nextLoad(url, context);
  },
});
