import { ProCard, ProFormDependency, ProFormText, ProFormUploadButton } from "@ant-design/pro-components"
import { requireRules } from "../../../utils/util";
import type { UploadFile } from "antd";
import { memo, useState } from "react";
import { API } from "../../../api";
import { axiosJsonPost } from "../../../api/request";
import { noop } from "antd/es/_util/warning";
import { invokeMakeCommonProFormUploadButtonCustomRequest } from "../../../utils/uploadUtils";

const Index = () => {




    return (
        <ProCard bordered={true} className="!mb-[20px]">

            <ProFormText
                label="配置文件密码"
                name="contentDecodePasswd"
                rules={requireRules}
            />

            <ProFormDependency
                name={['contentDecodePasswd']}
            >
                {
                    ({ contentDecodePasswd }) => {
                        return (
                            <ProFormUploadButton
                                label="配置文件"
                                name="meteFileId"
                                rules={requireRules}
                                listType="text"
                                action={API.upload}

                                max={1}
                                title={!contentDecodePasswd ? '请先填写配置文件密码' : '选择并上传配置文件'}
                                disabled={!contentDecodePasswd}
                                formItemProps={{
                                    validateFirst: true,
                                    rules: [
                                        {
                                            required: true,

                                            validator(rule, value) {

                                                return new Promise<void>((resolve, reject) => {
                                                    if (!value?.length) {
                                                        reject("请上传配置文件");
                                                    } else {
                                                        console.log('value', value)
                                                        // const status = value[0]?.status;
                                                        setTimeout(async () => {
                                                            value = value[0]
                                                            if (value?.response?.code === 200) {
                                                                if (!contentDecodePasswd) {
                                                                    resolve()
                                                                } else {
                                                                    const res = await axiosJsonPost(API.validMetaFile, {
                                                                        meteFileId: value.response.data.id,
                                                                        contentDecodePasswd
                                                                    })
                                                                    if (res.code === 200) {
                                                                        const msg = res.data.errors?.join(',')

                                                                        if (msg) {
                                                                            reject(msg)
                                                                        } else {
                                                                            resolve()
                                                                        }
                                                                    } else {
                                                                        // TODO:
                                                                        // resolve()
                                                                        reject(res.msg)
                                                                    }
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
                                            validateTrigger: 'onSubmit'
                                        },
                                        {
                                            required: true,
                                            validator(rule, value) {

                                                return new Promise<void>(async (resolve, reject) => {
                                                    if (!value?.length) {
                                                        reject("请上传配置文件");
                                                    } else {
                                                        resolve()
                                                    }
                                                })


                                            },
                                            validateTrigger: 'onChange'
                                        }
                                    ]
                                }}
                                fieldProps={{
                                    onPreview: noop,
                                    customRequest: invokeMakeCommonProFormUploadButtonCustomRequest.bind(noop, API.upload)
                                }}



                            />
                        )
                    }
                }
            </ProFormDependency>



        </ProCard>

    )
}


export default memo(Index)