import { useNavigate } from "react-router-dom"
import { getRouteQuery, invokeGenPath, replaceRouter } from "../../../utils/routerUtils"
import { Tabs } from "antd"
import { memo, useCallback, useMemo, useState } from "react"
import qs from "qs"

const Index = ({
    memoTabItem
}) => {


    // const activeKey = getRouteQuery('tab')
    const [activeKey, setActiveKey] = useState(getRouteQuery('tab') || memoTabItem[0]?.key)

    // const navigate = useNavigate()

    const onChange = useCallback((e) => {
        replaceRouter({
            query: {
                tab: e
            }
        })
        // const query = getRouteQuery()
        // query.tab = e

        // const url = invokeGenPath(`${window.location.pathname}?${qs.stringify(query)}`)
        // history.replaceState(null, '', url);

        setActiveKey(e)
    }, [])

    return (
        <Tabs
            // key={key}
            activeKey={activeKey || memoTabItem[0]?.key}
            items={memoTabItem}
            onChange={onChange}
            destroyOnHidden={true}
        />
    )
}


export default memo(Index)