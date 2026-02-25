import { mapLabelByValue, mapValueByLabel } from "./utils/utils";

// Enum constants
const T_ENVIRONMENT = 'ENVIRONMENT'
const T_MIDDLEWARE = "MIDDLEWARE";
const T_APPLICATION = "APPLICATION";



const index = [
    {
        value: T_ENVIRONMENT,
        label: '基础组件',
    },
    {
        value: T_MIDDLEWARE,
        label: '中间件',
    },
    {
        value: T_APPLICATION,
        label: '应用',
    },
];

function getArtifactTypeValueByLabel(label) {
    return mapValueByLabel(index, label);
}

function getArtifactTypeLabelByValue(value) {
    return mapLabelByValue(index, value);
}



export {
    T_ENVIRONMENT,
    T_MIDDLEWARE,
    T_APPLICATION,
    getArtifactTypeValueByLabel,
    getArtifactTypeLabelByValue,
};
export default index;
