import { ProCard, ProFormUploadButton } from "@ant-design/pro-components";
import { requireRules } from "../../../utils/util";
import { API } from "../../../api";
import { noop } from "lodash-es";

const Index = () => {
    return (
        <ProCard bordered={true} className="!mb-[20px]">
            <ProFormUploadButton
                label="部署清单"
                name="deployFileId"
                rules={requireRules}

                max={1}
                listType="text"
                title='选择并上传部署清单'
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
                                        reject("请上传部署清单");
                                    } else {
                                        // console.log('value', value)
                                        // const status = value[0]?.status;
                                        setTimeout(async () => {
                                            value = value[0]
                                            if (value?.response?.code === 200) {
                                                resolve()
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



export default Index