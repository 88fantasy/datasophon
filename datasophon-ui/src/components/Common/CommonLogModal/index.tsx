import { useCallback, useEffect, useMemo, useState } from "react"
import CommonMonacoEditor from "../CommonMonacoEditor"
import { Button } from "antd"
import { CloseOutlined, FullscreenExitOutlined, FullscreenOutlined } from "@ant-design/icons"

const Index = ({
    api,
    onCancelClickProxy
}) => {

    const [logs, setLogs] = useState()
    const [wrapperClassName, setWrapperClassName] = useState()

    const invokeInit = useCallback(async () => {
        if (typeof api === 'function') {
            const res = await api()

            if (res.code === 200) {
                setLogs(res.data)
            }
        }
    }, [api])


    useEffect(() => {
        invokeInit()
    }, [invokeInit])

    const onFullSceenClick = useCallback(() => {
        setWrapperClassName(preState => {
            return preState ? '' : 'fixed h-[100vh] w-[100vw] left-0 top-0 p-[12px] bg-white '
        })
    }, [])

    const invokeRender = useCallback((full = false) => {
        return (
            <>

                <div className="mb-[10px] flex justify-between items-center">
                    <h3 className="text-[16px] font-bold ">日志</h3>
                    <div className="text-[16px]">
                        {
                            full ? <FullscreenExitOutlined
                                className="mr-[10px] cursor-pointer"
                                onClick={onFullSceenClick}
                            /> : <FullscreenOutlined
                                className="mr-[10px] cursor-pointer"
                                onClick={onFullSceenClick}
                            />
                        }
                        <CloseOutlined className="cursor-pointer" onClick={onCancelClickProxy} />
                    </div>
                </div>
                <div className="flex-1">
                    <CommonMonacoEditor
                        language="yaml"
                        value={logs}
                        options={{
                            minimap: { enabled: false },
                            readOnly: true,
                            wordWrap: 'on',         // 启用自动换行
                            automaticLayout: true,
                        }}
                    />
                </div>
                <div
                    className="mt-[10px] flex flex-col items-center"

                >
                    <Button
                        onClick={invokeInit}
                    >
                        刷新
                    </Button>
                </div>
            </>
        )
    }, [invokeInit, logs, onCancelClickProxy, onFullSceenClick])

    return (
        <>
            <div className={`h-[70vh] flex flex-col`}>
                {invokeRender()}
            </div>
            {
                wrapperClassName && (
                    <div className={`${wrapperClassName} flex flex-col`}>
                        {invokeRender(true)}
                    </div>
                )
            }
        </>
    )
}


export default Index