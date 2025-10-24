/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
import Index from ".";

export default async function (config) {


    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        width: '70vw',
        footer: false
    };

    return defineModalApi({
        config
    });
}
