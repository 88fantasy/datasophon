import { useState, useCallback, useMemo, useEffect, useActionState, useRef, use, forwardRef, useImperativeHandle } from 'react';
import { Button, message } from 'antd';
import CommonMonacoEditor from '../../../../../components/Common/CommonMonacoEditor';
import EditorWrapper from './EditorWrapper';
import { mergeYamlFiles } from '../../../../../utils/yamlUtils';
import { T_HELM } from '../../../../../components/Common/CommonLogModal/api';


const Index = ({
    record,
    handleSave
}, ref) => {
    const [middleEditorValue, setMiddleEditorValue] = useState(record.deltaValues || '');
    const [defaultValue, setDefaultValue] = useState(record.values)
    const [valuePath, setValuePath] = useState()
    const [loading, setLoading] = useState(false);



    const memoCommonEditorProps = useMemo(() => {
        // const suffix = selectedPath?.split('.')?.pop()?.toLowerCase();
        let lang: string | undefined = 'yaml';

        // if (suffix) {
        //     if (suffix === 'json') lang = 'json';
        //     if (suffix === 'yaml' || suffix === 'yml') lang = 'yaml';
        //     if (suffix === 'txt') lang = 'plaintext';
        // }

        return {
            language: lang,
            // filename: selectedPath,
        };
    }, []);

    const onOkClickProxy = useCallback(() => {
        handleSave?.({
            record,
            middleEditorValue
        })
    }, [handleSave, middleEditorValue, record])

    // const handleSave = useCallback(async () => {
    //     // if (typeof onConfirm === 'function') {
    //     //     try {
    //     //         await onConfirm({
    //     //             path: selectedPath,
    //     //             content: middleEditorValue,
    //     //         });
    //     //         message.success('保存成功');
    //     //     } catch (error) {
    //     //         console.error(error);
    //     //         message.error('保存失败');
    //     //     }
    //     // }
    //     const res = await axiosJsonPost(API.updateK8sInstanceValues, {
    //         id: record.id,
    //         deltaValues: middleEditorValue,
    //     })

    //     showMsgAfferRequest(res)


    //     if (res.code === 200) {
    //     }

    // }, [middleEditorValue, onOkClickProxy, record.id])


    const memoRightValue = useMemo(() => {


        if (record.metaFileType === T_HELM) {
            return mergeYamlFiles(defaultValue || '', middleEditorValue)
        } else {
            return defaultValue
        }

    }, [defaultValue, middleEditorValue, record.metaFileType])


    useEffect(() => {
        setMiddleEditorValue(record.deltaValues || '')
    }, [record.deltaValues])


    useEffect(() => {
        setDefaultValue(record.values || '')
    }, [record.values])

    useImperativeHandle(ref, () => {
        return {
            middleEditorValue,
            defaultValue,
            record
        }
    })

    return (
        <div className="flex-1 flex flex-col gap-4 ">

            <div className="flex gap-3 flex-1">


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

            {
                handleSave && <div className='flex flex-row-reverse'>
                    <Button type="primary" onClick={onOkClickProxy} loading={loading}>
                        保存
                    </Button>
                </div>
            }

        </div>
    );
};

export default forwardRef(Index);
