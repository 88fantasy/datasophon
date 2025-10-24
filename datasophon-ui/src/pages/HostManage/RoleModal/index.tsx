import { useEffect, useState } from "react"
import { API } from "../../../api"
import { axiosPost } from "../../../api/request"
import { Empty, Spin, Tag } from "antd"


const colorMap = {
    1: 'success',
    2: 'error'
}
const Index = ({
    clusterId,
    record
}) => {


    const [dataSource, setDataSource] = useState()

    const invokeInit = async () => {

        const params = {
            clusterId,
            hostname: record.hostname || "",
        };
        const res = await axiosPost(API.getRoleListByHostname, params)


        if (res.code === 200) {
            setDataSource(res.data || [])
        }
    }


    useEffect(() => {
        invokeInit()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])


    const invokeRender = () => {
        if (!dataSource) {
            return <Spin />
        } else {
            if (dataSource.length) {
                return (
                    <div>
                        {dataSource.map(item => (
                            <Tag
                                className="!mb-[10px]"
                                color={colorMap[item.serviceRoleStateCode] || 'warning'} key={item.id}
                            >
                                {item.serviceRoleName}
                            </Tag>
                        ))}

                    </div>
                )
            } else {
                return <Empty description="暂无数据" />
            }
        }
    }

    return invokeRender()
}


export default Index