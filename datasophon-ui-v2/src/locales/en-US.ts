import additionalMonitor from './en-US/additionalMonitor';
import apisixGateway from './en-US/apisixGateway';
import apisixMonitor from './en-US/apisixMonitor';
import clusterDashboard from './en-US/clusterDashboard';
import component from './en-US/component';
import dolphinSchedulerMonitor from './en-US/dolphinSchedulerMonitor';
import dorisActiveTask from './en-US/dorisActiveTask';
import dorisMonitor from './en-US/dorisMonitor';
import dsWorkflow from './en-US/dsWorkflow';
import globalHeader from './en-US/globalHeader';
import gravitinoMonitor from './en-US/gravitinoMonitor';
import juicefsMonitor from './en-US/juicefsMonitor';
import lineage from './en-US/lineage';
import menu from './en-US/menu';
import nacosMonitor from './en-US/nacosMonitor';
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
  ...dsWorkflow,
  ...dorisMonitor,
  ...dorisActiveTask,
  ...nacosMonitor,
  ...gravitinoMonitor,
  ...nginxMonitor,
  ...valkeyMonitor,
  ...apisixMonitor,
  ...rustfsMonitor,
  ...juicefsMonitor,
  ...additionalMonitor,
  ...clusterDashboard,
  ...lineage,
  ...apisixGateway,
};
