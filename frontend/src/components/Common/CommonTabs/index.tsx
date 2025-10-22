import { useNavigate } from "react-router-dom"
import { getRouteQuery, invokeGenPath, replaceRouter } from "../../../utils/routerUtils"
import { Tabs } from "antd"
import { memo, Suspense, useCallback, useEffect, useMemo, useState } from "react"
import qs from "qs"

const Index = ({
    memoTabItem,
    // className
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
        console.log('memoTabItem')
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


    useEffect(() => {
        const obj = items.find(val => val.key === activeKey)
        if (!obj) {
            setActiveKey(items[0]?.key)
        }

    }, [items, activeKey])

    return (
        <Tabs
            // key={key}
            // className={className}
            activeKey={activeKey || memoTabItem[0]?.key}
            items={items}
            onChange={onChange}
            destroyOnHidden={true}
        />
    )
}


export default memo(Index)