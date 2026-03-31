import { useState, useCallback, useMemo, useEffect, useActionState, useRef, use } from 'react';
import { Button, message } from 'antd';
import CommonMonacoEditor from '../../../../../components/Common/CommonMonacoEditor';
import { axiosJsonPost } from '../../../../../api/request';
import { API } from '../../../../../api';
import EditorWrapper from './EditorWrapper';
import { showMsgAfferRequest } from '../../../../../utils/util';
import { mergeYamlFiles } from '../../../../../utils/yamlUtils';


const Index = ({
    record,
    onOkClickProxy
}) => {
    const [middleEditorValue, setMiddleEditorValue] = useState(record.deltaValues || '');
    const [defaultValue, setDefaultValue] = useState(record.value)
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
        const res = await axiosJsonPost(API.updateK8sInstanceValues, {
            id: record.id,
            deltaValues: middleEditorValue,
        })

        showMsgAfferRequest(res)


        if (res.code === 200) {
            onOkClickProxy?.()
        }

    }, [middleEditorValue, onOkClickProxy, record.id])


    const memoRightValue = useMemo(() => {

        const finalValues = mergeYamlFiles(defaultValue || '', middleEditorValue)

        return finalValues
    }, [defaultValue, middleEditorValue])


    useEffect(() => {
        setMiddleEditorValue(record.deltaValues || '')
    }, [record.deltaValues])


    useEffect(() => {
        setDefaultValue(record.value)
    }, [record.value])



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

            <div className='flex flex-row-reverse'>
                <Button type="primary" onClick={handleSave} loading={loading}>
                    保存
                </Button>
            </div>
        </div>
    );
};

export default Index;
