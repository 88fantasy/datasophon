import apisixMonitor from './zh-CN/apisixMonitor';
import component from './zh-CN/component';
import dolphinSchedulerMonitor from './zh-CN/dolphinSchedulerMonitor';
import dorisMonitor from './zh-CN/dorisMonitor';
import globalHeader from './zh-CN/globalHeader';
import juicefsMonitor from './zh-CN/juicefsMonitor';
import menu from './zh-CN/menu';
import network from './zh-CN/network';
import nginxMonitor from './zh-CN/nginxMonitor';
import pages from './zh-CN/pages';
import prometheusMonitor from './zh-CN/prometheusMonitor';
import rustfsMonitor from './zh-CN/rustfsMonitor';
import settingDrawer from './zh-CN/settingDrawer';
import settings from './zh-CN/settings';
import valkeyMonitor from './zh-CN/valkeyMonitor';
import zookeeperMonitor from './zh-CN/zookeeperMonitor';

export default {
  'navBar.lang': '语言',
  'layout.user.link.help': '帮助',
  'layout.user.link.privacy': '隐私',
  'layout.user.link.terms': '条款',
  'app.preview.down.block': '下载此页面到本地项目',
  ...pages,
  ...globalHeader,
  ...menu,
  ...settingDrawer,
  ...settings,
  ...network,
  ...component,
  ...prometheusMonitor,
  ...zookeeperMonitor,
  ...dolphinSchedulerMonitor,
  ...dorisMonitor,
  ...nginxMonitor,
  ...valkeyMonitor,
  ...apisixMonitor,
  ...rustfsMonitor,
  ...juicefsMonitor,
};
