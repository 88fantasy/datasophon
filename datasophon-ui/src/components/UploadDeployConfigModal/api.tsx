/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../Common/CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {


    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: '导入部署清单',
        classNames: {
            body: 'max-h-[70vh] mt-[20px] overflow-hidden'
        },
        okText: '开始部署',
    };

    return defineModalApi({
        config
    });
}

