/* eslint-disable react-refresh/only-export-components */
import defineModalApi from "../../../../components/Common/CommonModal/DefineModal/api";
import Index from ".";
import { API } from "../../../../api";
import { axiosPost } from "../../../../api/request";

export default async function (config) {



    const getRoleGroupListRes = await axiosPost(
        API.getRoleGroupList,
        {
            serviceInstanceId: config.serviceInstanceId
        }
    )


    config.groupList = getRoleGroupListRes.data || []





    // .then((res) => {
    //     if (res.code !== 200) return
    //     this.GroupList = res.data
    //     if (this.GroupList.length > 0) {
    //         this.currentList = this.GroupList[0].id;
    //         this.handleSubmit()
    //     }

    // })

    config.render = conf => {
        return <Index {...conf} />;
    };

    config.dialogConfig = {
        title: '服务版本对比',
        width: '70vw',
        // footer: false,
        okText: '重启过时服务',
        cancelButtonProps: {
            className: '!hidden'

        },
        classNames: {
            body: 'h-[60vh] overflow-auto'
        }

    };

    return defineModalApi({
        config
    });
}
