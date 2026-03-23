import { mapLabelByValue, mapValueByLabel } from './utils/utils';

// Enum constants
const T_CONFIG_FILE = 'config_file';
const T_TOKEN = 'token';
const T_PASSWORD = 'password';

const index = [
  {
    value: T_CONFIG_FILE,
    label: '配置文件',
  },
  {
    value: T_TOKEN,
    label: 'token认证',
  },
  {
    value: T_PASSWORD,
    label: '用户名/密码',
  },
];

function getConnectionTypeValueByLabel(label) {
  return mapValueByLabel(index, label);
}

function getConnectionTypeLabelByValue(value) {
  return mapLabelByValue(index, value);
}

export {
  T_CONFIG_FILE,
  T_TOKEN,
  T_PASSWORD,
  getConnectionTypeValueByLabel,
  getConnectionTypeLabelByValue,
};

export default index;
