/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../Common/CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {


    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: '导入',
        classNames: {
            body: 'max-h-[70vh] overflow-y-auto mt-[20px] overflow-hidden'
        },
        footer: false,
    };

    return defineModalApi({
        config
    });
}

