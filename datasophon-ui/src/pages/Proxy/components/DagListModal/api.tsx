/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {


    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: '部署状况',
        width: '80vw',
        footer: false
    };

    return defineModalApi({
        config
    });
}
