import { ProFormText, ProFormUploadButton } from "@ant-design/pro-components"
import { requireRules } from "../../../utils/util";
import type { UploadFile } from "antd";
import { memo, useState } from "react";

const Index = () => {


    const [fileList, setFileList] = useState<UploadFile[]>([]);


    return (
        <>

            <ProFormUploadButton
                label="配置文件"
                name="meteFileId"
                rules={requireRules}
                // max={1}
                // fileList={fileList}
                fieldProps={{
                    maxCount: 1
                    // customRequest
                    // beforeUpload: (file) => {
                    //     setFileList([...fileList, file]);
                    //     return false;
                    // },
                    // onRemove: (file) => {
                    //     const index = fileList.indexOf(file);
                    //     const newFileList = fileList.slice();
                    //     newFileList.splice(index, 1);
                    //     setFileList(newFileList);
                    // },
                }}


            />

        </>

    )
}


export default Index