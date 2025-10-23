// RedirectOldProject.tsx
import { useCallback, useEffect, useState } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { axiosPost } from '../../api/request';
import { API } from '../../api';


const Index = () => {
    const { clusterId, } = useParams()
    const [instanceId, setInstanceId] = useState()

    const invokeGetServiceListByClusterProxy = useCallback(async () => {
        const res = await axiosPost(API.getServiceListByCluster, {
            clusterId
        })

        if (res.code === 200) {
            setInstanceId(res.data?.[0]?.id)
            // setServiceList(res.data)
            // serviceListMapRef.current = res.data.reduce((acc, val) => {
            //     acc[val.id] = val
            //     return acc
            // }, {})
        }
        return res
    }, [clusterId])


    useEffect(() => {
        invokeGetServiceListByClusterProxy()
    }, [])


    return instanceId && <Navigate to={`Instance/${instanceId}`} replace />;
}
export default Index