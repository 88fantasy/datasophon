#!/usr/bin/env node
/**
 * 把 node_modules/monaco-editor/min/vs 同步到 public/monaco/vs，
 * 供 src/monacoLoader.ts 在纯离线环境下从本地静态目录加载 Monaco Editor
 * （替代 @monaco-editor/loader 默认指向 cdn.jsdelivr.net 的行为）。
 *
 * 幂等：public/monaco/.version 与 monaco-editor 的 package.json 版本一致时跳过。
 */
import { createRequire } from 'node:module';
import {
  cpSync,
  existsSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const __dirname = dirname(fileURLToPath(import.meta.url));
const rootDir = join(__dirname, '..');

const srcDir = join(rootDir, 'node_modules', 'monaco-editor', 'min', 'vs');
const destDir = join(rootDir, 'public', 'monaco', 'vs');
const versionFile = join(rootDir, 'public', 'monaco', '.version');

if (!existsSync(srcDir)) {
  console.error(
    `[copy-monaco] 找不到 ${srcDir}，monaco-editor 依赖未安装或版本目录结构变化，终止构建。`,
  );
  process.exit(1);
}

const monacoVersion = require('monaco-editor/package.json').version;

if (existsSync(versionFile)) {
  const currentVersion = readFileSync(versionFile, 'utf-8').trim();
  if (currentVersion === monacoVersion && existsSync(destDir)) {
    console.log(`[copy-monaco] public/monaco/vs 已是最新版本 ${monacoVersion}，跳过。`);
    process.exit(0);
  }
}

if (existsSync(destDir)) {
  rmSync(destDir, { recursive: true, force: true });
}
cpSync(srcDir, destDir, { recursive: true });
writeFileSync(versionFile, monacoVersion);

console.log(`[copy-monaco] 已同步 monaco-editor@${monacoVersion} 到 public/monaco/vs`);
