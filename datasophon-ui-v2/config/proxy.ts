/**
 * @name 代理的配置
 * @see 在生产环境 代理是无法生效的，所以这里没有生产环境的配置
 * -------------------------------
 * The agent cannot take effect in the production environment
 * so there is no configuration of the production environment
 * For details, please see
 * https://pro.ant.design/docs/deploy
 *
 * @doc https://umijs.org/docs/guides/proxy
 */
export default {
  dev: {
    // 将 /ddh 请求代理到 datasophon-api 后端；页面导航（text/html）跳过代理
    // 后端 context-path=/ddh，前端 baseURL=/ddh/api/v2
    '/ddh': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      bypass(req: import('http').IncomingMessage) {
        // 页面路由请求（Accept: text/html）不代理，由 dev server 返回 SPA index.html
        const accept = req.headers['accept'];
        if (accept?.includes('text/html')) return req.url ?? '/';
        return null;
      },
    },
  },
  /**
   * @name 详细的代理配置
   * @doc https://github.com/chimurai/http-proxy-middleware
   */
  test: {
    // localhost:8000/api/** -> https://pro-api.ant-design-demo.workers.dev/api/**
    '/api/': {
      target: 'https://pro-api.ant-design-demo.workers.dev',
      changeOrigin: true,
    },
  },
  pre: {
    '/api/': {
      target: 'your pre url',
      changeOrigin: true,
    },
  },
};
