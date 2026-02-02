/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {

    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        // title: '日志',
        footer: false,
        closable: false,
        // className: 'w-[80vh]'
        width: '80vw',
        // className: 'h-[70vh]'
        classNames: {
            body: 'h-[70vh] overflow-auto'
        }
    };

    return defineModalApi({
        config
    });
}
