import { ProCard } from "@ant-design/pro-components"
import CommonTabs from "../../../../components/Common/CommonTabs"
import { useCallback, useMemo, useState } from "react"
import clusterType, { T_K8S, T_PHYSICAL } from "../../../../constants/clusterType"
import { cloneDeep } from "lodash-es"
import CommonTable from "../../../../components/Common/CommonTable"
import { getRouteQuery } from "../../../../utils/routerUtils"


const editTabKey = 'clusterTypeTab'
const Index = ({
    frameServiceList,
    frameK8sServiceList,
    columns,
    k8sColumns
}) => {

    const [state, setState] = useState(getRouteQuery(editTabKey) || T_PHYSICAL)

    const request = useCallback(async (params, sort, filter) => {
        // console.log(params, sort, filter);
        let data
        if (state === T_PHYSICAL) {
            data = frameServiceList
        } else if (state === T_K8S) {
            data = frameK8sServiceList
        }

        if (!data) {
            data = []
        }

        return {
            data,
            total: data.length
        }
    }, [frameK8sServiceList, frameServiceList, state])

    const memoColumns = useMemo(() => {
        let res = []
        if (state === T_PHYSICAL) {
            res = columns
        } else if (T_K8S === state) {
            res = k8sColumns
        }


        return res  
    }, [columns, k8sColumns, state])

    const memoTabList = useMemo(() => {
        return cloneDeep(clusterType).map(val => {
            val.key = val.value
            val.children = (
                <CommonTable
                    tableProps={{
                        search: false,
                        request,
                        scroll: {
                            y: '46vh'
                        },
                        columns: memoColumns,
                    }}
                />
            )

            return val
        })

    }, [memoColumns, request])


    const onBeforeChange = useCallback(({ next }) => {
        setState(next)
    }, [])


    return (

        <CommonTabs
            tabKey={editTabKey}
            memoTabItem={memoTabList}
            bindUrl={true}
            onBeforeChange={onBeforeChange}
            type="card"
        />
    )
}


export default Index