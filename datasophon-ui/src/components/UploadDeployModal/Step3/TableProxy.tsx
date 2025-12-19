import { useCallback, useEffect, useMemo, useState } from "react"
import * as yaml from 'js-yaml';
import { sm4 } from 'sm-crypto';
import CommonTable from "../../Common/CommonTable";

const Index = (props) => {


    const {
        deployFileId,
        contentDecodePasswd
    } = props

    const [state, setState] = useState({})

    const invokeInit = useCallback(async () => {

        const yamlData = deployFileId && await new Promise((resolve) => {
            const file = deployFileId?.[0]?.originFileObj;
            const reader = new FileReader();

            reader.onload = function (event) {
                const yamlText = event.target.result;

                let content = yamlText


                try {
                    content = sm4.decrypt(content, contentDecodePasswd)
                } catch (error) {
                    console.warn('解密失败', error)
                }

                try {


                    content = yaml.load(content); // 使用 js-yaml 解析
                    console.log('loadcontent', content)
                    content = JSON.parse(JSON.stringify(content, null, 2));
                } catch (err) {
                    console.warn('YAML 解析错误:', err);
                    content = undefined
                    // document.getElementById('output').textContent = '解析失败: ' + err.message;
                }

                resolve(content)
            };


            if (file) {

                console.log('file', file)
                reader.readAsText(file);

            }
        })

        try {
            if (yamlData?.app) {
                setState(preState => {
                    return {
                        ...preState,
                        dataSource: yamlData.app || []
                    }
                })
            }
        } catch (error) {
            console.warn('YAML 解析错误:', error);
        }
    }, [contentDecodePasswd, deployFileId])


    const columns = useMemo(() => {
        return [
            {
                title: '制品',
                dataIndex: 'name',
                ellipsis: true,
                search: false,
            },
            {
                title: '版本',
                dataIndex: 'version',
                ellipsis: true,
                search: false,
            },
        ]
    }, [])

    useEffect(() => {
        invokeInit()
    }, [invokeInit])

    return (
        <div className="mb-[10px]">
            <CommonTable
                tableProps={{
                    dataSource: state.dataSource || [],
                    columns,
                    scroll: {
                        y: '30vh'
                    },
                    className: 'h-full',
                    tableAlertRender: false,
                    toolBarRender: false,
                    search: false,

                }}
            />
        </div>
    )
}


export default Index