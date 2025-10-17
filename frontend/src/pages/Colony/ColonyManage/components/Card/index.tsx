import { CheckCircleOutlined, ClockCircleOutlined, ForkOutlined } from "@ant-design/icons"
import { ProCard, ProForm } from "@ant-design/pro-components"
import { Button, Card, Col, Empty } from "antd"
import Meta from "antd/es/card/Meta"
import { blue, gold, gray, red } from '@ant-design/colors';
import { invokeRenderSimpleDetails } from "../../../../../components/Common/CommonDetails";
import { showComfirmModal, showMsgAfferRequest } from "../../../../../utils/util";
import { axiosPostUpload } from "../../../../../api/request";
import { API } from "../../../../../api";
import { noop } from "lodash-es";
import { invokeGenPath } from "../../../../../utils/routerUtils";
import { useParams } from "react-router-dom";

const showAuthModal = () =>
    import("../AuthModal/api");
const showBuildOrEditModal = () =>
    import("../BuildOrEditModal/api");
const showConfigModal = () =>
    import("../ConfigModal/api");


const Index = ({
    val,
    invokeInit
}) => {

    const {
        add,
        clusterStateCode
    } = val


    const bgClassMap = {
        2: 'bg-[#0279FE14]'
    }

    const iconClassMap = {
        2: blue.primary,
        3: red.primary,
    }

    const bgClass = bgClassMap[clusterStateCode] || 'bg-[#FF88331E]'

    const iconColor = iconClassMap[clusterStateCode] || gold.primary

    const title = (
        <div className={`flex items-center justify-center rotate-180 w-[40px] h-[40px] rounded-[50%] ${bgClass}`}>
            <ForkOutlined
                style={{
                    fontSize: 20,
                    color: iconColor
                }}
            />
        </div>
    )

    const iconMap = {
        2: <CheckCircleOutlined

            style={{
                color: iconColor
            }}
        />
    }


    const statusIcon = iconMap[clusterStateCode] || <ClockCircleOutlined
        style={{
            color: iconColor
        }}
    />

    const columns = [
        {
            title: '集群管理员',
            dataIndex: () => {
                return (val.clusterManagerList || [])
                    .map(val => {
                        return val.username
                    })
                    .join(',')
            }
        },
        {
            title: '创建时间',
            dataIndex: 'createTime'
        }
    ]

    const onEditOrBuildClick = async (record) => {
        const modelApi = await showBuildOrEditModal()
        modelApi.default({
            record,
            onOk: () => {
                invokeInit()
            }
        })
    }


    const actions = [
        {
            label: '授权',
            onClick: async () => {
                const modelApi = await showAuthModal()
                modelApi.default({
                    record: val,
                    onOk: () => {
                        invokeInit()
                    }
                })
            }
        },
        {
            label: '编辑',
            disabled: clusterStateCode === 2,
            onClick: onEditOrBuildClick.bind(noop, val)
        },
        {
            label: '进入',
            disabled: clusterStateCode !== 2,
            onClick: () => {
                console.log('edit')

                window.open(invokeGenPath(`/Cluster/${val.id}/Overview/Index`))
            }
        },
        {
            label: '配置',
            disabled: clusterStateCode === 2,
            onClick: async () => {
                const modelApi = await showConfigModal()
                modelApi.default({
                    record: val,
                    onOk: () => {
                        invokeInit()
                    }
                })
            }
        },
        {
            label: '删除',
            color: 'danger',
            disabled: clusterStateCode === 2,
            onClick: async () => {
                let res = await showComfirmModal({
                    content: '确定要删除该集群吗？',
                    okType: 'danger'
                })

                if (res) {
                    const params = JSON.stringify([val.id])
                    res = axiosPostUpload(API.deleteColony, params)

                    showMsgAfferRequest(res)


                    if (res.code === 200) {
                        invokeInit()

                    }
                }
            }
        }
    ].map(v => {
        return (
            <Button
                color={v.disabled ? 'default' : v.color || 'primary'}
                variant="text"
                onClick={v.onClick}
                disabled={v.disabled}
                key={v.label}
            >
                {v.label}
            </Button>
        )
    })



    return (
        <Col span={8} className="mb-[20px]"
        >
            {
                !add ? (
                    <Card
                        classNames={{
                            header: 'h-[60px]',
                            title: 'h-[60px] !flex items-center '
                        }}
                        title={
                            <>
                                <Meta
                                    className="flex items-center flex-1"
                                    avatar={title}
                                    title={val.clusterName}
                                />
                                <div className="flex items-center text-[14px] font-normal">
                                    {
                                        statusIcon
                                    }
                                    <span
                                        className="ml-[5px]"
                                        style={{
                                            color: 'var(--ant-color-text-secondary)'
                                        }}
                                    >
                                        {val.clusterState}
                                    </span>

                                </div>
                            </>
                        }
                        actions={actions}
                    >
                        {
                            invokeRenderSimpleDetails(columns, val)
                        }

                    </Card>
                ) : (
                    <Card
                        classNames={{
                            body: 'h-[208px] flex items-center justify-center'
                        }}
                    >

                        <Empty
                            image={
                                <ForkOutlined
                                    className="rotate-180"
                                    style={{
                                        fontSize: 60,
                                        color: gray.primary
                                    }}
                                />
                            }
                            description={false}
                            styles={{ image: { height: 60 } }}
                        >
                            <Button
                                type="primary"
                                onClick={onEditOrBuildClick}
                            >
                                创建
                            </Button>
                        </Empty>

                    </Card>
                )
            }

        </Col >
    )
}


export default Index

