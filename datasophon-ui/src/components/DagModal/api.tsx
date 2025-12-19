/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../Common/CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {


    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: '角色',
        width: '50vw',
        footer: false
    };

    return defineModalApi({
        config
    });
}
