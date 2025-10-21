import { useCallback, useEffect, useState } from "react"
import CommonMonacoEditor from "../CommonMonacoEditor"
import { Button } from "antd"

const Index = ({
    api
}) => {

    const [logs, setLogs] = useState()


    const invokeInit = useCallback(async () => {
        if (typeof api === 'function') {
            const res = await api()

            if (res.code === 200) {
                setLogs(res.data)
            }
        }
    }, [])


    useEffect(() => {
        invokeInit()
    }, [])

    return (
        <div className="h-[64vh] flex flex-col">
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
        </div>
    )
}


export default Index