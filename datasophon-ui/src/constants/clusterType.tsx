import { mapLabelByValue, mapValueByLabel } from "./utils/utils";

// Enum constants
const T_PHYSICAL = 'physical'
const T_K8S = 'k8s'

const index = [
    {
        value: T_PHYSICAL,
        label: '物理机',
    },
    {
        value: T_K8S,
        label: 'K8S集群',
    },
];

function getClusterTypeValueByLabel(label) {
    return mapValueByLabel(index, label);
}

function getClusterTypeLabelByValue(value) {
    return mapLabelByValue(index, value);
}

export {
    T_PHYSICAL,
    T_K8S,
    getClusterTypeValueByLabel,
    getClusterTypeLabelByValue,
};
export default index;
