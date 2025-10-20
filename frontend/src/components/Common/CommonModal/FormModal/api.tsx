/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../DefineModal/api";
import Index from ".";

export default async function (config) {

    if (config.columns && !config.formConfig) {
        config.formConfig = config.columns
            .filter(val => {
                return val.com
            }).map(val => {
                return {
                    // ...val,
                    name: val.dataIndex || val.name,
                    label: val.title || val.label,
                    ...(val?.formItemProps || {}),
                    // ...val,
                    com: val.com
                }
            })
    }

    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: config.title || `${config?.record?.id ? '编辑' : '新增'}`,
        classNames: {
            body: 'max-h-[60vh] overflow-auto'
        }
    };

    return defineModalApi({
        config
    });
}
