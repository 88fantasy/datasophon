import apisixMonitor from './en-US/apisixMonitor';
import component from './en-US/component';
import dolphinSchedulerMonitor from './en-US/dolphinSchedulerMonitor';
import dorisMonitor from './en-US/dorisMonitor';
import globalHeader from './en-US/globalHeader';
import menu from './en-US/menu';
import network from './en-US/network';
import nginxMonitor from './en-US/nginxMonitor';
import pages from './en-US/pages';
import prometheusMonitor from './en-US/prometheusMonitor';
import rustfsMonitor from './en-US/rustfsMonitor';
import settingDrawer from './en-US/settingDrawer';
import settings from './en-US/settings';
import valkeyMonitor from './en-US/valkeyMonitor';
import zookeeperMonitor from './en-US/zookeeperMonitor';

export default {
  'navBar.lang': 'Languages',
  'layout.user.link.help': 'Help',
  'layout.user.link.privacy': 'Privacy',
  'layout.user.link.terms': 'Terms',
  'app.preview.down.block': 'Download this page to your local project',
  ...globalHeader,
  ...menu,
  ...settingDrawer,
  ...settings,
  ...network,
  ...component,
  ...pages,
  ...prometheusMonitor,
  ...zookeeperMonitor,
  ...dolphinSchedulerMonitor,
  ...dorisMonitor,
  ...nginxMonitor,
  ...valkeyMonitor,
  ...apisixMonitor,
  ...rustfsMonitor,
};
