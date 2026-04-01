import { mapLabelByValue, mapValueByLabel } from "./utils/utils";

// Enum constants
const T_POD = 'pod'
const T_DEPLOYMENT = 'deployment'
const T_SERVICE = 'service'
const T_INGRESS = 'ingress'
const T_CONFIGMAP = 'configmap'

const index = [
    {
        value: T_POD,
        label: 'Pod 类型',
    },
    {
        value: T_DEPLOYMENT,
        label: '部署类型',
    },
    {
        value: T_SERVICE,
        label: '服务类型',
    },
    {
        value: T_INGRESS,
        label: '入口类型',
    },
    {
        value: T_CONFIGMAP,
        label: '配置映射类型',
    },
];

function getResourceTypeValueByLabel(label) {
    return mapValueByLabel(index, label);
}

function getResourceTypeLabelByValue(value) {
    return mapLabelByValue(index, value);
}

export {
    T_POD,
    T_DEPLOYMENT,
    T_SERVICE,
    T_INGRESS,
    T_CONFIGMAP,
    getResourceTypeValueByLabel,
    getResourceTypeLabelByValue,
};
export default index;
