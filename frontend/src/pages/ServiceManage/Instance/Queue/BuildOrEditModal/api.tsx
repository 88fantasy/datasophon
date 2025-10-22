/* eslint-disable react-refresh/only-export-components */
// import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
// import Index from ".";


import defineModalApi from "../../../../../components/Common/CommonModal/DefineModal/api";
import Index from '.'



export default async function (config) {
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
