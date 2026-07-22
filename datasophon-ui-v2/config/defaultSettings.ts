import type { ProLayoutProps } from '@ant-design/pro-components';
import { LOGO_URL } from './publicPath';

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
  logo: LOGO_URL,
  iconfontUrl: '',
  token: {
    // 参见ts声明，demo 见文档，通过token 修改样式
    //https://procomponents.ant.design/components/layout#%E9%80%9A%E8%BF%87-token-%E4%BF%AE%E6%94%B9%E6%A0%B7%E5%BC%8F
    // 顶栏适当加高，呼应设计稿更舒展的留白节奏。
    // 注意：不覆盖 header 的颜色类 token（colorBgHeader 等），
    // 那些由 navTheme 驱动，写死会废掉 SettingDrawer 的暗色顶栏切换。
    header: {
      heightLayoutHeader: 60,
    },
  },
};

export default Settings;
