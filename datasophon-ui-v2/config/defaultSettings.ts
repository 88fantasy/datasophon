import type { ProLayoutProps } from '@ant-design/pro-components';

/**
 * @name
 */
const Settings: ProLayoutProps & {
  logo?: string;
} = {
  navTheme: 'light',
  colorPrimary: '#1677ff',
  layout: 'top',
  contentWidth: 'Fluid',
  fixedHeader: false,
  fixSiderbar: true,
  colorWeak: false,
  title: 'DataSophon',
  // 与 config/config.ts 的 PUBLIC_PATH 保持同一计算方式：本文件既被 webpack 打进浏览器包，
  // 又被 umi layout 插件在 Node 端直接 require 用于生成 Layout.tsx，process.env.PUBLIC_PATH
  // 只在浏览器打包场景下经 define 配置替换，Node 端读不到，所以这里不能依赖它，只能用两端都
  // 可靠存在的 NODE_ENV 自己算
  logo: `${process.env.NODE_ENV === 'development' ? '/' : '/ddh/static/'}logo.svg`,
  iconfontUrl: '',
  token: {
    // 参见ts声明，demo 见文档，通过token 修改样式
    //https://procomponents.ant.design/components/layout#%E9%80%9A%E8%BF%87-token-%E4%BF%AE%E6%94%B9%E6%A0%B7%E5%BC%8F
  },
};

export default Settings;
