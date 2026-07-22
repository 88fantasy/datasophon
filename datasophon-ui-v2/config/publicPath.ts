// umi publicPath 配置项：部署时的静态资源路径，如果部署在非根目录下需要配置这个变量
// （@doc https://umijs.org/docs/api/config#publicpath）。dev 环境用 '/'，避免静态资源被
// /ddh proxy 规则拦截转发到后端导致 504。
//
// 本文件既被 webpack 打进浏览器包（前端页面 import LOGO_URL），又被 config.ts / umi layout
// 插件在 Node 端直接 require，process.env.PUBLIC_PATH 只在浏览器打包场景下经 define 配置替换，
// Node 端读不到，所以这里不能依赖它，只能用两端都可靠存在的 NODE_ENV 自己算。零依赖（不 import
// 任何本地模块），避免与 import 了本文件的 config.ts 循环。
export const PUBLIC_PATH: string = process.env.NODE_ENV === 'development' ? '/' : '/ddh/static/';

export const LOGO_URL = `${PUBLIC_PATH}logo.svg`;
