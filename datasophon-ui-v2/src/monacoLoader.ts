// 让 @monaco-editor/react 从本地静态目录加载 Monaco Editor，
// 而不是默认的 cdn.jsdelivr.net（纯离线环境下不可达）。
// public/monaco/vs 由 scripts/copy-monaco.mjs 在 predev/prestart/prebuild 时同步。
// 必须在任何 <Editor /> 挂载之前执行——loader.config 一旦开始加载 vs/loader.js 就不再接受配置变更。
import { loader } from '@monaco-editor/react';
import { PUBLIC_PATH } from '@root/config/publicPath';

loader.config({ paths: { vs: `${PUBLIC_PATH}monaco/vs` } });
