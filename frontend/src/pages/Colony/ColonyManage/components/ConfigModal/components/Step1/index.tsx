import { ProFormText, ProFormTextArea } from "@ant-design/pro-components"
import { Alert } from "antd"
import { requireRules } from "../../../../../../../utils/util"

const Index = () => {
    return (
        <>
            <Alert className='!mb-[10px] w-full' message="提示：使用IP或主机名输入主机列表，按逗号分隔或使用主机域批量添加主机，例如：10.3.144.[19-23]" />
            <ProFormTextArea
                label="主机列表"
                name={'hosts'}
                formItemProps={{
                    rules: requireRules
                }}
            />
            <ProFormText
                label="SSH用户名"
                name={"sshUser"}
                formItemProps={{
                    rules: requireRules,
                    initialValue: 'root'
                }}
            />
            <ProFormText
                label="SSH端口"
                name={"sshPort"}
                formItemProps={{
                    rules: requireRules,
                    initialValue: '22'
                }}
            />
        </>
    )
}


export default Index