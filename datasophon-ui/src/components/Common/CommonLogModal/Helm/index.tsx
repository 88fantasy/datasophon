import { useState, useCallback, useMemo, useEffect, useActionState, useRef } from 'react';
import { ProForm, ProFormTreeSelect } from '@ant-design/pro-components';
import { Button, message } from 'antd';
import CommonMonacoEditor from '../../CommonMonacoEditor';
import { axiosJsonPost } from '../../../../api/request';
import { API } from '../../../../api';
import EditorWrapper from './EditorWrapper';
import { showMsgAfferRequest } from '../../../../utils/util';
import { mergeYamlFiles, parseYaml } from '../../../../utils/yamlUtils';


const Index = ({
    treeDataApi,
    metaInfo,
    onConfirm,
    record,
    projectEnvId,
    initialValues = {},
    onOkClickProxy
}) => {
    const [selectedPath, setSelectedPath] = useState<string | undefined>(initialValues.path || null);
    const [treeData, setTreeData] = useState([]);
    const [leftEditorValue, setLeftEditorValue] = useState(initialValues.leftContent || '');
    const [middleEditorValue, setMiddleEditorValue] = useState(initialValues.middleContent || '');
    const [rightEditorValue, setRightEditorValue] = useState(initialValues.rightContent || '');
    const [defaultValue, setDefaultValue] = useState()
    const [chartValue, setChartValue] = useState()
    const [valuePath, setValuePath] = useState()
    const [loading, setLoading] = useState(false);


    const memoChartValueJsonData = useMemo(() => {
        return parseYaml(chartValue)
    }, [chartValue])


    // const invokeFetch


    const invokeGetEditValue = useCallback(async (path) => {
        if (!path || !metaInfo?.id) {
            return;
        }

        try {
            const res = await axiosJsonPost(API.projectEnvGetEditedFileContent, {
                artifactMetaInfoId: metaInfo.id,
                projectEnvId,
                artifactVersionId: record.artifactVersionId,
                fileName: path,

            });


            if (res.code === 200) {
                const content = res.data ?? '';
                // setLeftEditorValue(content);

                return content
                // setMiddleEditorValue(content);
                // setRightEditorValue(content);
            } else {
                showMsgAfferRequest(res)
            }
        } catch (error) {
            message.error('加载文件内容失败');
            console.error(error);
        }


    }, [metaInfo.id, projectEnvId, record.artifactVersionId])

    const invokeFetchFileContent = useCallback(async (path) => {
        if (!path || !metaInfo?.id) {
            return;
        }

        setLoading(true);
        try {
            const res = await axiosJsonPost(API.artifactMetaPreview, {
                id: metaInfo.id,
                fileName: path,
            });


            if (res.code === 200) {
                const content = res.data ?? '';
                // setLeftEditorValue(content);

                return content
                // setMiddleEditorValue(content);
                // setRightEditorValue(content);
            } else {
                showMsgAfferRequest(res)

            }
        } catch (error) {
            message.error('加载文件内容失败');
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [metaInfo?.id])


    const fetchFileContent = useCallback(async (path) => {

        setLeftEditorValue('')
        const res = await invokeFetchFileContent(path)

        if (res) {
            setLeftEditorValue(res);
        }

    }, [invokeFetchFileContent]);


    const invokeInitTreeData = useCallback(async () => {
        if (typeof treeDataApi === 'function') {
            try {
                const res = await treeDataApi();
                if (res.code === 200) {
                    setTreeData(res.data || []);
                }

                const valuesPath = res.flaterData?.find(val => {
                    return /values.yaml$/.test(val)
                })
                const chartPath = res.flaterData?.find(val => {
                    return /Chart.yaml$/.test(val)
                })


                const reqArr = [
                    valuesPath && invokeFetchFileContent(valuesPath),
                    chartPath && invokeFetchFileContent(chartPath),
                    valuesPath && invokeGetEditValue(valuesPath)
                ]

                const [valuesPathRes, chartPathRes, invokeGetEditValueRes] = await Promise.all(reqArr)

                if (valuesPathRes) {
                    setValuePath(valuesPath)

                    if (valuesPathRes) {
                        setDefaultValue(valuesPathRes)
                    }

                    if (invokeGetEditValueRes) {
                        setMiddleEditorValue(invokeGetEditValueRes);
                    }
                }

                if (chartPathRes) {
                    setChartValue(chartPathRes)
                }


            } catch (error) {
                console.error('Failed to load tree data:', error);
                message.error('加载文件树失败');
            }
        }
    }, [invokeFetchFileContent, invokeGetEditValue, treeDataApi]);

    useEffect(() => {
        invokeInitTreeData();
    }, [invokeInitTreeData]);



    const memoCommonEditorProps = useMemo(() => {
        const suffix = selectedPath?.split('.')?.pop()?.toLowerCase();
        let lang: string | undefined = 'yaml';

        if (suffix) {
            if (suffix === 'json') lang = 'json';
            if (suffix === 'yaml' || suffix === 'yml') lang = 'yaml';
            if (suffix === 'txt') lang = 'plaintext';
        }

        return {
            language: lang,
            filename: selectedPath,
        };
    }, [selectedPath]);

    const onSelect = useCallback((value) => {
        // Find the node
        const findNode = (nodes, val) => {
            for (const node of nodes) {
                if (node.path === val) {
                    return node;
                }
                if (node.children) {
                    const found = findNode(node.children, val);
                    if (found) return found;
                }
            }
            return null;
        };
        const node = findNode(treeData, value);
        if (node && node.children && node.children.length > 0) {
            // formRef.current?.setFieldsValue({
            //     path: selectedPath,
            // });
            return;
        }

        setSelectedPath(value);
        fetchFileContent(value);

    }, [fetchFileContent, treeData])

    const handleSave = useCallback(async () => {
        // if (typeof onConfirm === 'function') {
        //     try {
        //         await onConfirm({
        //             path: selectedPath,
        //             content: middleEditorValue,
        //         });
        //         message.success('保存成功');
        //     } catch (error) {
        //         console.error(error);
        //         message.error('保存失败');
        //     }
        // }
        const res = await axiosJsonPost(API.projectEnvSaveEditedFileContent, {
            artifactMetaInfoId: metaInfo.id,
            projectEnvId,
            artifactVersionId: record.artifactVersionId,
            content: middleEditorValue,
            fileName: valuePath
        })

        showMsgAfferRequest(res)


        if (res.code === 200) {
            onOkClickProxy()
        }

    }, [metaInfo, middleEditorValue, onOkClickProxy, projectEnvId, record.artifactVersionId, valuePath])

    useEffect(() => {
        // 无论是否成功加载，右边预览展示中间编辑结果
        // setRightEditorValue(middleEditorValue);
    }, [middleEditorValue]);


    const memoRightValue = useMemo(() => {

        const finalValues = mergeYamlFiles(defaultValue || '', middleEditorValue)

        return finalValues
        // if (!selectedPath) {
        //     if (defaultValue) {
        //         return finalValues
        //     }
        // } else if (leftEditorValue) {

        //     const leftEditorValueJson = parseYaml(leftEditorValue)
        //     console.log('leftEditorValueJson', leftEditorValueJson)
        //     const advancedHelmRenderer = new AdvancedHelmRenderer(finalValues, {
        //         chart: memoChartValueJsonData,
        //         'hello-world': {
        //             fullName: '111'
        //         }

        //     })

        //     let res


        //     try {
        //         res = advancedHelmRenderer.helmTemplate({
        //             leftEditorValue
        //         })
        //         // console.log('res', res)
        //         // res = res.leftEditorValue
        //     } catch (error) {
        //         console.error(error)
        //     }

        //     return res


        // }
    }, [defaultValue, middleEditorValue])



    return (
        <div className="h-full flex flex-col gap-4 ">
            {
                treeDataApi && <div className="w-full">
                    <ProForm
                        layout="vertical"
                        submitter={false}
                        initialValues={{
                            path: selectedPath,
                        }}
                    >
                        <ProFormTreeSelect
                            name="path"
                            label="选择 Helm 文件"
                            placeholder="请选择 Helm 文件路径"
                            fieldProps={{
                                value: selectedPath,
                                // value: selectedPath,
                                treeData: treeData,
                                treeDefaultExpandAll: true,
                                fieldNames: {
                                    title: 'title',
                                    value: 'path',
                                },
                                onSelect,
                            }}
                            rules={[
                                {
                                    required: true,
                                    message: '请选择文件路径',
                                },
                            ]}
                        />
                    </ProForm>
                </div>
            }

            {/* 
            <div className="flex items-center justify-between gap-2">

                <span className="text-xs text-gray-500">当前文件: {selectedPath || '-'} </span>
            </div> */}

            <div className="flex gap-3 flex-1">
                {
                    treeDataApi && <EditorWrapper title={`原始内容（只读）${selectedPath || ''}`}>
                        <CommonMonacoEditor
                            language={memoCommonEditorProps.language || 'plaintext'}
                            value={leftEditorValue}
                            options={{
                                minimap: { enabled: false },
                                readOnly: true,
                                wordWrap: 'on',
                                automaticLayout: true,
                            }}
                        />
                    </EditorWrapper>
                }


                <EditorWrapper title={`编辑内容 ${valuePath || ''}`}>
                    <CommonMonacoEditor
                        {...memoCommonEditorProps}
                        value={middleEditorValue}
                        onChange={(value) => {
                            setMiddleEditorValue(value || '');
                        }}
                        options={{
                            minimap: { enabled: false },
                            readOnly: false,
                            wordWrap: 'on',
                            automaticLayout: true,
                        }}
                    />
                </EditorWrapper>

                <EditorWrapper title={`实时预览 ${valuePath || ''}`}>
                    <CommonMonacoEditor
                        language={memoCommonEditorProps.language || 'plaintext'}
                        value={memoRightValue}
                        options={{
                            minimap: { enabled: false },
                            readOnly: true,
                            wordWrap: 'on',
                            automaticLayout: true,
                        }}
                    />
                </EditorWrapper>
            </div>

            <div className='flex flex-row-reverse'>
                <Button type="primary" onClick={handleSave} loading={loading}>
                    保存
                </Button>
            </div>
        </div>
    );
};

export default Index;
