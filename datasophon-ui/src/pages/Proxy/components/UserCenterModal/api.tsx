/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {


    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: '用户中心',
        width: '30vw',
        footer: false
    };

    return defineModalApi({
        config
    });
}
