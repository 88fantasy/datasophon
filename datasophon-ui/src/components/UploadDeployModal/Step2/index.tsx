import { ProCard, ProFormDependency, ProFormText, ProFormUploadButton } from "@ant-design/pro-components"
import { requireRules } from "../../../utils/util";
import type { UploadFile } from "antd";
import { memo, useState } from "react";
import { API } from "../../../api";
import { axiosJsonPost } from "../../../api/request";
import { noop } from "lodash-es";

const Index = () => {


    return (

        <ProFormDependency
            name={['meteFileId', 'contentDecodePasswd']}
        >
            {
                ({ meteFileId, contentDecodePasswd }) => {
                    meteFileId = meteFileId?.[0]?.response?.data?.id
                    return meteFileId && contentDecodePasswd && (
                        <ProCard bordered={true} className="!mb-[20px]">

                            <ProFormUploadButton
                                label="部署包"
                                name="pkgFileId"
                                rules={requireRules}
                                max={1}
                                title={meteFileId ? '选择并上传部署文件' : '请先上传配置文件'}
                                disabled={!meteFileId}
                                listType="text"
                                action={API.upload}
                                fieldProps={{
                                    onPreview: noop
                                }}
                                formItemProps={{
                                    rules: [
                                        {
                                            required: true,
                                            validator(rule, value) {

                                                return new Promise<void>(async (resolve, reject) => {
                                                    if (!value?.length) {
                                                        reject("请上传部署包");
                                                    } else {
                                                        // console.log('value', value)
                                                        // const status = value[0]?.status;
                                                        setTimeout(async () => {
                                                            value = value[0]
                                                            if (value?.response?.code === 200) {
                                                                const res = await axiosJsonPost(API.validatePkgFile, {
                                                                    pkgFileId: value.response.data.id,
                                                                    meteFileId,
                                                                    contentDecodePasswd
                                                                })
                                                                if (
                                                                    res.code === 200
                                                                ) {
                                                                    const msg = res.data.errors?.join(',')

                                                                    if (msg) {
                                                                        reject(msg)
                                                                    } else {
                                                                        resolve()
                                                                    }
                                                                } else {
                                                                    TODO:
                                                                    // resolve()
                                                                    reject(res.msg)
                                                                }
                                                            } else if (value?.status === 'uploading') {
                                                                reject('正在上传中,请稍后重试')
                                                            } else {
                                                                reject('上传失败,请重新上传后重试')
                                                            }
                                                        }, 1 * 1000)

                                                    }
                                                })


                                            },
                                        }
                                    ]
                                }}
                            />
                        </ProCard>

                    )
                }
            }


        </ProFormDependency>



    )
}


export default Index