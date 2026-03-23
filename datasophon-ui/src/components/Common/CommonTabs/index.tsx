import { getRouteQuery, invokeGenPath, replaceRouter } from "../../../utils/routerUtils"
import { Spin, Tabs } from "antd"
import { memo, Suspense, useCallback, useEffect, useMemo, useState } from "react"

const Index = (props) => {

    const {
        tabBarExtraContent,
        memoTabItem,
        bindUrl,
        onBeforeChange,
        destroyOnHidden,
        rootClassName,
        defActiveKey,
        tabKey = 'tab',
        type
    } = props


    // const activeKey = getRouteQuery('tab')
    const [activeKey, setActiveKey] = useState(defActiveKey || getRouteQuery(tabKey) || memoTabItem[0]?.key)

    // const navigate = useNavigate()

    const onChange = useCallback(async (e) => {


        const onBeforeChangeRes = await onBeforeChange?.({
            current: activeKey,
            next: e
        })


        if (onBeforeChangeRes !== false) {

            if (bindUrl) {
                replaceRouter({
                    query: {
                        ...getRouteQuery(),
                        [tabKey]: e
                    }
                })

            }


            setActiveKey(e)
        }

    }, [activeKey, bindUrl, onBeforeChange, tabKey])

    const items = useMemo(() => {
        console.log('memoTabItem')
        return memoTabItem
            .filter(Boolean)
            .map(val => {
                if (!val.children && val.asyncChildren) {
                    const Com = val.asyncChildren
                    val.children = (
                        <Suspense fallback={
                            <Spin
                                className="  left-[50%] top-[50%] transform-[translate(-50%,-50%)]"
                            />
                        }>

                            <Com

                                ref={val.ref}
                                {...(val.props || {})}
                            />
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
            tabBarExtraContent={tabBarExtraContent}
            activeKey={activeKey || memoTabItem[0]?.key}
            items={items}
            onChange={onChange}
            destroyOnHidden={typeof destroyOnHidden === 'undefined' ? true : destroyOnHidden}
            rootClassName={rootClassName}
            type={type}
        />
    )
}


export default memo(Index)