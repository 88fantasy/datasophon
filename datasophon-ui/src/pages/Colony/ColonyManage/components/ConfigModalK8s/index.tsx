import { useCallback, useRef } from 'react';
import { ProForm, ProFormSelect, ProFormText, ProFormDependency, ProFormTextArea, ProFormItemRender, pickControlPropsWithId } from '@ant-design/pro-components';
import { Button, message } from 'antd';
import { requireRules, showMsgAfferRequest } from '../../../../../utils/util';
import connectionTypeOptions, { T_CONFIG_FILE, T_PASSWORD, T_TOKEN } from '../../../../../constants/connectionType';
import { axiosJsonPost } from '../../../../../api/request';
import { API } from '../../../../../api';
import CommonMonacoEditor from '../../../../../components/Common/CommonMonacoEditor';

const Index = ({
    onCancelClickProxy,
    record
}) => {

    const commonMonacoEditorRef = useRef()

    const onTestClick = useCallback(async (values) => {
        // 这里接入实际请求接口，例如 axiosPost(API.testCluster, values)
        const clusterId = record?.id

        values.clusterId = clusterId


        const res = await axiosJsonPost(API.clusterK8sConfigTestConnection, values)


        showMsgAfferRequest(res)
    }, [record?.id]);


    const onFinishClick = useCallback(async (values) => {

        const clusterId = record?.id
        values.clusterId = clusterId


        const res = await axiosJsonPost(API.saveOrUpdateK8sConfig, values);

        showMsgAfferRequest(res);


        if (res.code === 200) {
            onCancelClickProxy()
        }

        return res.code === 200;
    }, [onCancelClickProxy, record?.id])

    return (
        <ProForm
            submitter={{
                render: (props, doms) => {
                    return (
                        <div className='flex items-center justify-end gap-[10px]'>

                            <Button
                                key="test"
                                type="default"
                                onClick={async () => {
                                    const values = await props.form?.validateFields();
                                    await onTestClick(values);
                                }}
                            >
                                测试连通性
                            </Button>
                            {...doms}

                        </div>
                    );
                },
                resetButtonProps: false
            }}
            onFinish={onFinishClick}


            initialValues={{ type: T_CONFIG_FILE }}
        >
            <ProFormSelect
                name="type"
                label="连接方式"
                options={connectionTypeOptions}
                rules={requireRules}
            />



            <ProFormDependency name={['type']}>
                {({ type }) => {
                    const res = []

                    if ([T_TOKEN, T_PASSWORD].includes(type)) {
                        res.push(
                            <ProFormText
                                name="serverHost"
                                label="K8S主机名称"
                                rules={requireRules}
                            />,

                            <ProFormTextArea
                                name="serverCert"
                                label="K8S证书"
                                rules={requireRules}
                            />,


                        )
                    }


                    if (T_TOKEN === type) {
                        res.push(
                            <ProFormTextArea
                                name="token"
                                label="Token"
                                rules={requireRules}
                            />
                        )
                    } else if (type === T_PASSWORD) {
                        res.push(
                            <ProFormText
                                name="username"
                                label="	用户名"
                                rules={requireRules}
                            />,

                            <ProFormText.Password
                                name="password"
                                label="密码"
                                rules={requireRules}
                            />,
                        )
                    } else if (type === T_CONFIG_FILE) {
                        res.push(
                            <ProFormItemRender
                                label="配置文件"
                                name="kubeConfig"
                                rules={requireRules}
                            >

                                {
                                    (itemProps) => {
                                        const {
                                            value,
                                            onChange,
                                        } = pickControlPropsWithId(itemProps)

                                        return <div className=" h-[50vh]">
                                            <CommonMonacoEditor
                                                language="yaml"
                                                ref={commonMonacoEditorRef}
                                                options={{
                                                    minimap: { enabled: false },
                                                    wordWrap: 'on',         // 启用自动换行
                                                    automaticLayout: true,
                                                }}
                                                value={value}
                                                onChange={onChange}
                                            />
                                        </div>
                                    }
                                }
                            </ProFormItemRender>

                        )
                    }


                    return res;
                }}
            </ProFormDependency>
        </ProForm>
    );
};

export default Index;