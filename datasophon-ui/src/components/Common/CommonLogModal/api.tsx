/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {

    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: '日志',
        footer: false,
        // classNames: {
        //     body: 'max-h-[60vh] overflow-auto'
        // }
    };

    return defineModalApi({
        config
    });
}
