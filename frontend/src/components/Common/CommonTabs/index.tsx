import { useNavigate } from "react-router-dom"
import { getRouteQuery, invokeGenPath, replaceRouter } from "../../../utils/routerUtils"
import { Tabs } from "antd"
import { memo, Suspense, useCallback, useMemo, useState } from "react"
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

        setActiveKey(e)
    }, [])

    const items = useMemo(() => {
        return memoTabItem
            .filter(Boolean)
            .map(val => {
                if (!val.children && val.asyncChildren) {
                    const Com = val.asyncChildren
                    val.children = (
                        <Suspense fallback={<div>Loading...</div>}>
                            <Com />
                        </Suspense>
                    )
                }
                return val
            })
    }, [memoTabItem])

    return (
        <Tabs
            // key={key}
            activeKey={activeKey || memoTabItem[0]?.key}
            items={items}
            onChange={onChange}
            destroyOnHidden={true}
        />
    )
}


export default memo(Index)