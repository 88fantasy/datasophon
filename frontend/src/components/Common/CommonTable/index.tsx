import { PlusOutlined } from "@ant-design/icons";
import { ProTable, TableDropdown, type ActionType } from "@ant-design/pro-components";
import { Button } from "antd";
import { cloneDeep, isBoolean, noop } from "lodash-es";
import { useRef } from "react";
import { showComfirmModal, showMsgAfferRequest } from "../../../utils/util";

const showFormModal = () =>
    import("../CommonModal/FormModal/api");

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


// eslint-disable-next-line react-refresh/only-export-components
export const invokeGenOptionCol = (list, config) => {

    if (typeof config === 'function') {
        config = config()
    }
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
                        const titleKey = val.titleKey
                        console.log('titleKey', titleKey)
                        const showComfirmModalRes = await showComfirmModal({
                            content: `确定要删除${titleKey ? `${args[1]?.[titleKey] ? `【${args[1]?.[titleKey]}】` : ''}` : ''}吗？`,
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
                } else if (/edit|编辑/.test(val.title || val.key) && /Object/.test(Object.prototype.toString.call(val.config))) {
                    const bakClick = val.onClick || noop
                    val.onClick = async () => {
                        const modelApi = await showFormModal()
                        modelApi.default({
                            columns: list,
                            ...val.config,
                            record: args?.[2],
                            onOk: (...onOkArgs) => {
                                bakClick(...args, onOkArgs)
                            }
                        })

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
    const innnerActionRef = useRef<ActionType>();
    const actionRef = tableProps.actionRef || innnerActionRef



    return (
        <ProTable
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
            scroll={{
                y: '50vh'
            }}
            dateFormatter="string"
            toolBarRender={() => [
                tableProps?.onBuildClick && <Button
                    key="button"
                    icon={<PlusOutlined />}
                    onClick={() => {
                        tableProps?.onBuildClick?.({ action: actionRef.current })
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