import { useCallback, useEffect, useMemo, useState } from 'react';
import ReactDiffViewer from 'react-diff-viewer';
import { axiosPost } from '../../../../api/request';
import { API } from '../../../../api';
import { Empty, Menu } from 'antd';
import { isEmpty, showMsgAfferRequest } from '../../../../utils/util';

const Index = (props) => {

    const {
        groupList,
        serviceInstanceId,
        invokeInjectConfirmEvent
    } = props

    const [selectedKeys, setSelectedKeys] = useState(() => {
        const id = groupList?.[0]?.id
        return !isEmpty(id) ? [String(id)] : undefined
    })

    const [configVersionCompare, setConfigVersionCompare] = useState()

    const memoRoleGroupList = useMemo(() => {
        const res = groupList.map(val => {
            return {
                key: val.id,
                label: val.roleGroupName
            }
        })

        return res

    }, [groupList])

    const invokeGetConfigVersionCompare = useCallback(async (roleGroupId) => {
        const res = await axiosPost(
            API.configVersionCompare,
            {
                serviceInstanceId,
                roleGroupId
            }
        )



        if (res.code === 200) {
            try {
                res.data.oldStr = JSON.stringify(res.data.oldConfig, null, 4)
                res.data.newStr = JSON.stringify(res.data.newConfig, null, 4)
            } catch (error) {
                console.warn(error)
            }
            setConfigVersionCompare(res.data)
        }

    }, [serviceInstanceId])


    const onMenuClick = useCallback(({ key }) => {
        setSelectedKeys([key])
        invokeGetConfigVersionCompare(key)
    }, [invokeGetConfigVersionCompare])


    const onComfirm = useCallback(async () => {
        const params = {
            "roleGroupId": selectedKeys,
        }
        const res = await axiosPost(
            API.restartObsoleteService,
            params
        )

        showMsgAfferRequest(res)

        return res.code === 200
    }, [selectedKeys])


    useEffect(() => {
        const id = props.groupList?.[0]?.id

        if (id) {
            invokeGetConfigVersionCompare(id)
        }

    }, [invokeGetConfigVersionCompare, props.groupList])



    useEffect(() => {
        invokeInjectConfirmEvent(onComfirm)
    }, [invokeInjectConfirmEvent, onComfirm])



    return memoRoleGroupList?.length ? (
        <div className="flex ">


            <Menu
                className="w-[200px] h-full !border-none"
                items={memoRoleGroupList}
                selectedKeys={selectedKeys}
                onClick={onMenuClick}
            />
            <div className="flex-1">
                {
                    configVersionCompare ? <ReactDiffViewer
                        oldValue={configVersionCompare.oldStr}
                        newValue={configVersionCompare.newStr}
                        splitView={true}
                    /> :
                        <Empty description="请选择角色组" />
                }
            </div>



        </div>
    ) : <Empty />
}



export default Index