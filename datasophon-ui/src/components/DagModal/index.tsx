import { useCallback, useEffect, useRef, useState } from "react"
import { axiosJsonPost } from "../../api/request"
import { API } from "../../api"
import DataProcessingDagNode from "./DataProcessingDagNode"
import {
    Graph,
    Path,
    Edge,
    IS_SAFARI,
    Selection
} from '@antv/x6'
import { invokeGenerateElId } from "../../utils/util"
import { invokeGenPort, invokeGenSourceAndTarget } from "../../utils/antvUtils"
import gobalEvent, { uiEvent } from "../../utils/gobalEvent"
import { getRouteQuery } from "../../utils/routerUtils"
import { AntVDagreLayout } from "@antv/layout"


DataProcessingDagNode.invokeInit()

// 节点状态列表
const nodeStatusList = [
    {
        id: 'node-0',
        status: 'success',
    },
    {
        id: 'node-1',
        status: 'success',
    },
    {
        id: 'node-2',
        status: 'success',
    },
    {
        id: 'node-3',
        status: 'success',
    },
    {
        id: 'node-4',
        status: 'error',
        statusMsg: '错误信息示例',
    },
]

// 边状态列表
const edgeStatusList = [
    {
        id: 'edge-0',
        status: 'success',
    },
    {
        id: 'edge-1',
        status: 'success',
    },
    {
        id: 'edge-2',
        status: 'success',
    },
    {
        id: 'edge-3',
        status: 'success',
    },
]




const invokeTransferData = (data) => {
    const {
        nodes = [],
        edges = []
    } = data


    const mapNodes = []
    const mapEdges = []


    nodes.map(val => {
        val.id = String(val.id)
        const v = {
            id: val.id,
            shape: DataProcessingDagNode.shape,
            ports: invokeGenPort(val),
            data: val
        }
        mapNodes.push(v)
    })


    edges.map(val => {
        val.id = String(val.id)
        val.start = val.start && String(val.start)
        val.end = val.end && String(val.end)
        const v = {
            id: val.id,
            "shape": DataProcessingDagNode.edgeName,
            ...invokeGenSourceAndTarget(val.start, val.end)
        }


        mapEdges.push(v)
    })

    console.log(nodes, edges)
    return {
        nodes: mapNodes,
        edges: mapEdges
    }

}
const Index = (props) => {

    const graphRef = useRef()
    const updateTimeoutIdRef = useRef()
    const containerRef = useRef(invokeGenerateElId())

    const [state, setState] = useState({})







    const invokeInitGraph = useCallback(() => {

        const container = document.getElementById(containerRef.current);
        const wrapperEl = container.parentElement.parentElement
        const wrapperElStyle = window.getComputedStyle(container.parentElement)
        const graph: Graph = new Graph({
            interacting: false,
            container,
            width: wrapperEl.clientWidth - Number(wrapperElStyle.paddingLeft.replace(/px/, '')) - Number(wrapperElStyle.paddingRight.replace(/px/, '')),
            height: wrapperEl.clientHeight - Number(wrapperElStyle.paddingTop.replace(/px/, '')) - Number(wrapperElStyle.paddingBottom.replace(/px/, '')),
            panning: {
                enabled: true,
                eventTypes: ['leftMouseDown', 'mouseWheel'],
            },
            mousewheel: {
                enabled: true,
                modifiers: 'ctrl',
                factor: 1.1,
                maxScale: 1.5,
                minScale: 0.5,
            },
            highlighting: {
                magnetAdsorbed: {
                    name: 'stroke',
                    args: {
                        attrs: {
                            fill: '#fff',
                            stroke: '#31d0c6',
                            strokeWidth: 4,
                        },
                    },
                },
            },
            // edgeRouter: {
            //     name: 'manhattan', // 使用曼哈顿路径（推荐）
            //     args: {
            //         direction: 'horizontal', // 水平优先
            //     },
            // },
            connecting: {
                snap: true,
                allowBlank: false,
                allowLoop: false,
                highlight: true,

                sourceAnchor: {
                    name: 'left',
                    args: {
                        dx: IS_SAFARI ? 4 : 8,
                    },
                },
                targetAnchor: {
                    name: 'right',
                    args: {
                        dx: IS_SAFARI ? 4 : -8,
                    },
                },
                router: {
                    name: 'manhattan',

                    args: {
                        direction: 'horizontal',
                        padding: 10,
                    },
                },
                createEdge() {
                    return graph.createEdge({
                        shape: 'data-processing-curve',
                        attrs: {
                            line: {
                                strokeDasharray: '5 5',
                            },
                        },
                        zIndex: -1,
                    })
                },
                // 连接桩校验
                validateConnection({ sourceMagnet, targetMagnet }) {
                    // 只能从输出链接桩创建连接
                    if (!sourceMagnet || sourceMagnet.getAttribute('port-group') === 'in') {
                        return false
                    }
                    // 只能连接到输入链接桩
                    if (!targetMagnet || targetMagnet.getAttribute('port-group') !== 'in') {
                        return false
                    }
                    return true
                },
            },
        })

        graph.use(
            new Selection({
                multiple: true,
                rubberEdge: true,
                rubberNode: true,
                modifiers: 'shift',
                rubberband: true,
            }),
        )

        graphRef.current = graph


        return graph

    }, [])




    // 显示节点状态
    const showNodeStatus = useCallback(() => {
        nodeStatusList.forEach((item) => {
            const { id, status, statusMsg } = item
            const node = graphRef.current.getCellById(id)

            if (node) {
                const data = (node?.getData() || {})
                node.setData({
                    ...data,
                    status,
                    statusMsg,
                })
            }

        })
    }, [])

    // 开启边的运行动画
    const excuteAnimate = useCallback(() => {
        graphRef.current.getEdges().forEach((edge) => {
            edge.attr({
                line: {
                    stroke: '#3471F9',
                },
            })
            edge.attr('line/strokeDasharray', 5)
            edge.attr('line/style/animation', 'running-line 30s infinite linear')
        })
    }, [])

    // 关闭边的动画
    const stopAnimate = useCallback(() => {
        graphRef.current.getEdges().forEach((edge) => {
            edge.attr('line/strokeDasharray', 0)
            edge.attr('line/style/animation', '')
        })
        edgeStatusList.forEach((item) => {
            const { id, status } = item
            const edge = graphRef.current.getCellById(id)
            if (status === 'success') {
                edge?.attr('line/stroke', '#52c41a')
            }
            if (status === 'error') {
                edge?.attr('line/stroke', '#ff4d4f')
            }
        })
        // 默认选中一个节点
        graphRef.current.select('node-2')
    }, [])


    const invokeCancelUpdateTimeoutIdRef = useCallback(() => {
        if (updateTimeoutIdRef.current) {
            clearTimeout(updateTimeoutIdRef.current)
            updateTimeoutIdRef.current = undefined
        }
    }, [])

    const invokeInit = useCallback(async (update) => {
        const res = await axiosJsonPost(API.getDeployProgressDAG2, {
            dagId: getRouteQuery('dagId')

        })


        if (res.code === 200) {

            let data = invokeTransferData(res.data)


            if (!update) {

                // // 3. 应用 Dagre 自动布局
                // const dagreLayoutData = layout({
                //     nodes: data.nodes,
                //     edges: data.edges,
                //     type: 'dagre',
                //     rankdir: 'TB',     // TB: Top-Bottom（纵向）；LR: Left-Right（横向）
                //     ranksep: 80,       // 层级间距
                //     nodesep: 50,       // 节点间距
                //     controlPoints: true, // 生成控制点用于曲线/折线
                // });


                // 使用 Dagre 布局整理节点位置
                // const layout = new DagreLayout({
                //     // align: 'UL',
                //     rankdir: 'LR',
                //     nodesep: 80,   // 节点水平间距 → 增大以避免重叠
                //     ranksep: 100,  // 层级垂直间距 → 增大以拉开层级
                //     nodeSep: 50,   // 同一层级节点最小间距（部分库支持）
                //     edgeSep: 30,   // 边之间的最小间距（可选）
                //     minLen: 1,     // 强制每条边至少跨越一个层级（防止跨层过短）
                //     // nodeSize: [180, 60],
                //     // ranksep: 80,
                //     // nodesep: 30,
                //     // controlPoints: true,
                // });

                const layoutRes = new AntVDagreLayout({
                    // nodes: data.nodes,
                    // edges: data.edges,
                    rankdir: 'LR',
                    nodesep: 80,   // 节点水平间距 → 增大以避免重叠
                    ranksep: 100,  // 层级垂直间距 → 增大以拉开层级
                    nodeSep: 50,   // 同一层级节点最小间距（部分库支持）
                    edgeSep: 30,   // 边之间的最小间距（可选）
                    minLen: 1,
                })

                // layoutRes.

                // const layout = new Layout({
                //     type: 'elk',
                //     // nodes: data.nodes,
                //     // edges: data.edges,
                //     // ELK 配置（通过 options 传入）
                //     options: {
                //         direction: 'DOWN', // 等价于 'elk.direction'
                //         nodeNodeDistance: 80,
                //         layerNodeDistance: 100,
                //         edgeRouting: 'ORTHOGONAL', // 直角边
                //     },
                // })



                const layoutData = layoutRes.execute(data);

                graphRef.current.fromJSON(layoutData)

                setTimeout(() => {
                    excuteAnimate()
                    showNodeStatus()
                    gobalEvent.emit(uiEvent.updateDataProcessingDagNodeSize)
                }, 2000)
                setTimeout(() => {
                    showNodeStatus()
                    stopAnimate()
                }, 3000)
            } else {
                const dataMap = res.data?.nodes?.reduce((acc, curr) => {
                    acc[curr.id] = curr
                    return acc
                }, {})
                gobalEvent.emit(uiEvent.updateDataProcessingDagNodeData, dataMap)
            }


            updateTimeoutIdRef.current = setTimeout(() => {
                invokeInit(true)
            }, 3 * 1000)

        }
    }, [excuteAnimate, showNodeStatus, stopAnimate])


    useEffect(() => {
        return () => {
            invokeCancelUpdateTimeoutIdRef()
        }
    }, [invokeCancelUpdateTimeoutIdRef])


    useEffect(() => {
        invokeInitGraph()
        invokeInit()
    }, [invokeInit, invokeInitGraph])

    return (
        <div className="h-[100vh] w-[100vh]">
            <div id={containerRef.current}></div>
        </div>
    )
}


export default Index