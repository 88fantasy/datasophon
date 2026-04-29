import { use, useCallback, useEffect, useMemo, useRef, useState } from "react"
import CommonMonacoEditor from "../../CommonMonacoEditor"
import { Button, Cascader, Tabs } from "antd"
import { CloseOutlined, FullscreenExitOutlined, FullscreenOutlined } from "@ant-design/icons"
import { cloneDeep } from "lodash-es"
import { invokeGenerateElId } from "../../../../utils/util"

const invokeFindTab = (tabs, selectedTab) => {


    if (!tabs) {
        return
    }


    if (!selectedTab) {
        selectedTab = tabs[0]?.key || tabs[0]?.label
    }

    return tabs.find(val => {
        if (Object.prototype.hasOwnProperty.call(val, 'key')) {
            return val.key === selectedTab
        } else {
            return val.label === selectedTab
        }
    })
}

const Index = ({
    api,
    onCancelClickProxy,
    options = {},
    onOk,
    language = 'yaml'
}) => {

    const [logs, setLogs] = useState()
    const fullCommonMonacoEditorRef = useRef()
    const commonMonacoEditorRef = useRef()
    const [cascaderValue, setCascaderValue] = useState()
    const [wrapperClassName, setWrapperClassName] = useState()
    const [loading, setLoading] = useState()
    const [tabs, setTabs] = useState(() => {
        if (Array.isArray(api)) {
            return api.map(val => {
                return {
                    key: val.key || val.label,
                    label: val.label,
                    originData: val
                }
            })
        }
    })

    const [selectedTab, setSelectedTab] = useState(() => {
        return tabs?.[0]?.key || tabs?.[0]?.label
    })

    const memoSelectedTab = useMemo(() => {
        return invokeFindTab(tabs, selectedTab)
    }, [selectedTab, tabs])


    const invokeInit = useCallback(async (key = selectedTab) => {
        setLogs(undefined)

        if (typeof api === 'function') {
            const res = await api()

            if (res.code === 200) {
                setLogs(res.data)
            }
        } else if (Array.isArray(api)) {

            if (invokeFindTab(tabs, key)?.originData?.children?.length && !cascaderValue) {
                return
            }

            const apiObj = api.find(val => {
                if (Object.prototype.hasOwnProperty.call(val, 'key')) {
                    return val.key === key
                } else {
                    return val.label === key
                }
            })


            if (apiObj && typeof apiObj.api === 'function') {
                const res = await apiObj.api(cloneDeep(cascaderValue))

                if (res.code === 200) {
                    setLogs(res.data)
                }
            }
        }
    }, [api, cascaderValue, selectedTab, tabs])

    const onCascaderChange = useCallback(val => {
        setCascaderValue(val)
        // invokeInit()
    }, [setCascaderValue])


    const onOkProxy = useCallback(async () => {
        const currentRef = wrapperClassName ? fullCommonMonacoEditorRef : commonMonacoEditorRef


        const value = currentRef.current.editor.getValue()

        setLoading(true)
        const res = await onOk?.(value)
        setLoading(false)

        if (res !== false) {
            onCancelClickProxy()
        }
    }, [onCancelClickProxy, onOk, wrapperClassName])


    useEffect(() => {
        invokeInit()
    }, [invokeInit])

    const onFullSceenClick = useCallback((e) => {
        const {
            full
        } = e.currentTarget.dataset



        if (!options.readOnly) {
            let currentRef
            if (full === '0') {
                currentRef
                    = commonMonacoEditorRef
                setLogs(() => {
                    return value
                })
            } else if (full === '1') {
                currentRef = fullCommonMonacoEditorRef
                // commonMonacoEditorRef.current.editor.setValue(value)

            }
            const value = currentRef.current.editor.getValue()
            setLogs(() => {
                return value
            })
        }


        setWrapperClassName(preState => {
            return preState ? '' : 'fixed h-[100vh] w-[100vw] left-0 top-0 p-[12px] bg-white '
        })

    }, [options.readOnly])




    const onTabClick = useCallback((key) => {
        setCascaderValue(undefined)
        invokeInit(key)
        setSelectedTab(key)
        setLogs(undefined)
    }, [invokeInit, setCascaderValue])

    const invokeRender = useCallback((obj = {}) => {

        const {
            full
        } = obj

        const mapOptions = {
            minimap: { enabled: false },
            readOnly: true,
            wordWrap: 'on',         // 启用自动换行
            automaticLayout: true,
            ...options
        }


        return (
            <>

                <div className="mb-[10px] flex justify-between items-center">
                    <h3 className="text-[16px] font-bold ">{mapOptions.title || mapOptions.readOnly ? '日志' : logs ? '编辑' : '新建'}</h3>
                    <div className="text-[16px]">
                        {
                            full ? <FullscreenExitOutlined
                                className="mr-[10px] cursor-pointer"
                                data-full="1"
                                onClick={onFullSceenClick}
                            /> : <FullscreenOutlined
                                className="mr-[10px] cursor-pointer"
                                data-full="0"
                                onClick={onFullSceenClick}
                            />
                        }
                        <CloseOutlined className="cursor-pointer" onClick={onCancelClickProxy} />
                    </div>
                </div>

                {
                    !!tabs && <Tabs
                        items={tabs}
                        onTabClick={onTabClick}
                        activeKey={selectedTab}
                    />
                }
                {
                    !!memoSelectedTab?.originData?.children?.length && <Cascader
                        rootClassName="!w-[500px]"
                        key={selectedTab}
                        value={cascaderValue}
                        options={memoSelectedTab.originData.children}
                        onChange={onCascaderChange}
                        showCheckedStrategy={Cascader.SHOW_CHILD}
                        className="!mb-[10px]"
                        placeholder="请选择"
                    />
                }
                <div className="flex-1">
                    {
                        memoSelectedTab?.originData?.logRender ?
                            memoSelectedTab.originData.logRender({
                                key: invokeGenerateElId(),
                                logs
                            }) :
                            (
                                <CommonMonacoEditor
                                    key={selectedTab}
                                    language={language}
                                    value={logs}
                                    options={mapOptions}
                                    ref={full ? fullCommonMonacoEditorRef : commonMonacoEditorRef}
                                />
                            )

                    }

                </div>
                <div
                    className="mt-[10px] flex flex-col items-center"
                >

                    <Button
                        onClick={mapOptions.readOnly ? invokeInit : onOkProxy}
                        type={mapOptions.readOnly ? 'default' : 'primary'}
                        loading={loading}
                    >
                        {
                            mapOptions.readOnly ? '刷新' : '保存'
                        }
                    </Button>


                </div >
            </>
        )
    }, [cascaderValue, invokeInit, language, loading, logs, memoSelectedTab, onCancelClickProxy, onCascaderChange, onFullSceenClick, onOkProxy, onTabClick, options, selectedTab, tabs])

    return (
        <>
            {
                !wrapperClassName && (
                    <div className="h-full flex flex-col">
                        {invokeRender()}
                    </div>
                )
            }
            {
                wrapperClassName && (
                    <div className={`${wrapperClassName} flex flex-col`}>
                        {invokeRender({
                            full: true
                        })}
                    </div>
                )
            }
        </>
    )
}


export default Index