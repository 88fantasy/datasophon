import { PlusOutlined } from "@ant-design/icons";
import { ProTable, TableDropdown, type ActionType } from "@ant-design/pro-components";
import { Button } from "antd";
import { cloneDeep, isBoolean, noop } from "lodash-es";
import { useRef } from "react";
import { showComfirmModal, showMsgAfferRequest } from "../../../utils/util";

export type GithubIssueItem = {
    url: string;
    id: number;
    number: number;
    title: string;
    labels: {
        name: string;
        color: string;
    }[];
    state: string;
    comments: number;
    created_at: string;
    updated_at: string;
    closed_at?: string;
};


export const invokeGenOptionCol = (list, config) => {
    const fn = (arr, ...args) => {
        arr = cloneDeep(arr)

        const res = arr
            .filter(val => {
                let res

                if (isBoolean(val.disabled)) {
                    res = val.disabled
                } else if (typeof val.disabled === 'function') {
                    res = val.disabled(...args)
                }

                return !res
            })
            .map(val => {
                val.name = val.title = val.title || val.label
                val.key = val.key || val.title


                if (/delete|删除/.test(val.title || val.key)) {
                    const bakClick = val.onClick || noop
                    val.onClick = async () => {
                        const showComfirmModalRes = await showComfirmModal({
                            content: '确定要删除吗？',
                            okType: 'danger'
                        })

                        if (showComfirmModalRes) {
                            const res = await bakClick(...args)
                            console.log('res', res)

                            showMsgAfferRequest(res)

                            const action = args[args.length - 1]

                            action?.reload()
                        }
                    }
                }

                return (
                    <a
                        {...val}
                        onClick={val.onClick.bind(noop, ...args)}

                    >
                        {val.title}
                    </a>
                )
            })

        let domRes = res

        if (res.length > 3) {
            domRes = res.slice(0, 2)

            domRes.push(
                <TableDropdown
                    key="actionGroup"
                    menus={arr.splice(2, arr.length)}
                />
            )

        }

        return domRes
    }



    if (!config?.pure) {
        return (text, record, _, action) => {
            return fn(list, text, record, _, action)
        }
    }

    return fn(list)
}
const Index = ({
    tableProps
}) => {
    const actionRef = useRef<ActionType>();
    return (
        <ProTable<GithubIssueItem>
            actionRef={actionRef}
            cardBordered
            editable={{
                type: 'multiple',
            }}
            columnsState={{
                persistenceKey: 'pro-table-singe-demos',
                persistenceType: 'localStorage',
                defaultValue: {
                    option: { fixed: 'right', disable: true },
                },
                onChange(value) {
                    console.log('value: ', value);
                },
            }}
            rowKey="id"
            search={{
                labelWidth: 'auto',
            }}
            options={{
                setting: {
                    listsHeight: 400,
                },
            }}
            form={{
                // Since transform is configured, the submitted parameters are different from the defined ones, so they need to be transformed here
                syncToUrl: (values, type) => {
                    if (type === 'get') {
                        return {
                            ...values,
                        };
                    }
                    return values;
                },
            }}
            pagination={{
                pageSize: 10,
            }}
            dateFormatter="string"
            toolBarRender={() => [
                <Button
                    key="button"
                    icon={<PlusOutlined />}
                    onClick={() => {
                        actionRef.current?.reload();
                    }}
                    type="primary"
                >
                    新建
                </Button>,
            ]}
            {...tableProps}
        />
    );
}

export default Index