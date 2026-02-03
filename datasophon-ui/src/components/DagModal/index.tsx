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
import { invokeGenerateElId, showMsgAfferRequest } from "../../utils/util"
import { invokeGenPort, invokeGenSourceAndTarget } from "../../utils/antvUtils"
import gobalEvent, { uiEvent } from "../../utils/gobalEvent"
import { getRouteQuery } from "../../utils/routerUtils"
import { AntVDagreLayout, DagreLayout } from "@antv/layout"
import { blue, gold, green, grey, red } from "@ant-design/colors"
import { T_CANCEL, T_FAILED, T_PENDING, T_RUNNING, T_SUCCESS } from "./status"
import { Button } from "antd"
// import layout from '@antv/layout'


DataProcessingDagNode.invokeInit()


const lineStatus = {
    [T_SUCCESS]: {
        'line/stroke': green.primary,
        'line/strokeDasharray': 0,
        'line/style/animation': ''
    },
    [T_FAILED]: {
        'line/stroke': red.primary,
        'line/strokeDasharray': 0,
        'line/style/animation': ''
    },
    [T_CANCEL]: {
        'line/stroke': gold.primary,
        'line/strokeDasharray': 0,
        'line/style/animation': ''
    },
    [T_PENDING]: {
        'line/stroke': grey.primary,
        'line/strokeDasharray': 0,
        'line/style/animation': ''
    },
    [T_RUNNING]: {
        'line/stroke': blue.primary,
        'line/strokeDasharray': 5,
        'line/style/animation': 'running-line 30s infinite linear'
    }
}

const invokeTransferData = (data) => {
    const {
        nodes = [],
        edges = [],
        clusterId
    } = data


    const mapNodes = []
    const mapEdges = []
    const nodeMap = {}
    const nodeStatusMap = {}


    nodes.map(val => {
        val.id = String(val.id)
        val.clusterId = clusterId
        const v = {
            id: val.id,
            shape: DataProcessingDagNode.shape,
            ports: invokeGenPort(val),
            data: val,
            type: 'OUTPUT'
        }
        nodeMap[val.id] = val
        if (!nodeStatusMap[val.status]) {
            nodeStatusMap[val.status] = {}
        }
        nodeStatusMap[val.status][val.id] = val
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
        edges: mapEdges,
        nodeMap,
        nodeStatusMap
    }

}
const Index = (props) => {

    const graphRef = useRef()
    const updateTimeoutIdRef = useRef()
    const nodeMapRef = useRef({})
    const containerRef = useRef(invokeGenerateElId())




    const invokeInitGraph = useCallback(() => {

        const container = document.getElementById(containerRef.current);
        const wrapperEl = container.parentElement.parentElement
        const wrapperElStyle = window.getComputedStyle(container.parentElement)
        const graph: Graph = new Graph({
            // interacting: false,
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






    // 开启边的运行动画
    const updateAnimate = useCallback(() => {
        const edges = graphRef.current.getEdges()
        // console.log(' graphRef.current.getEdges()', edges)
        edges.forEach((edge) => {

            const {
                data
            } = edge

            const sourceObj = nodeMapRef.current[data.source]
            const statusByCommandState = lineStatus[sourceObj?.status]

            // console.log('statusByCommandState', nodeMapRef.current, statusByCommandState)
            if (statusByCommandState) {
                Object.keys(statusByCommandState)
                    .forEach(k => {
                        edge.attr(k, statusByCommandState[k])
                    })
            }
            // edge.attr({
            //     line: {
            //         stroke: '#3471F9',
            //     },
            // })
            // edge.attr('line/strokeDasharray', 5)
            // edge.attr('line/style/animation', 'running-line 30s infinite linear')
        })
    }, [])


    const invokeCancelUpdateTimeoutIdRef = useCallback(() => {
        if (updateTimeoutIdRef.current) {
            clearTimeout(updateTimeoutIdRef.current)
            updateTimeoutIdRef.current = undefined
        }
    }, [])


    const onRedeployClick = useCallback(async () => {
        const params = {
            dagId: getRouteQuery('dagId'),
            restart: true
        }

        const res = await axiosJsonPost(API.redeploy, params)

        showMsgAfferRequest(res)
    }, [])

    const invokeInit = useCallback(async (update) => {
        const res = await axiosJsonPost(API.getDeployProgressDAG2, {
            dagId: getRouteQuery('dagId')

        })


        if (res.code === 200) {

            let data = invokeTransferData(res.data)

            nodeMapRef.current = data.nodeMap

            // console.log('invokeTransferDatadata', data)


            if (!update) {


                // 使用 Dagre 布局整理节点位置
                const layout = new DagreLayout({
                    // align: 'UL',
                    rankdir: 'LR',
                    nodesep: 80,   // 节点水平间距 → 增大以避免重叠
                    ranksep: 100,  // 层级垂直间距 → 增大以拉开层级
                    nodeSep: 50,   // 同一层级节点最小间距（部分库支持）
                    edgeSep: 30,   // 边之间的最小间距（可选）
                    // minLen: 10,     // 强制每条边至少跨越一个层级（防止跨层过短）
                    // nodeSize: [180, 60],
                    // ranksep: 80,
                    // nodesep: 30,
                    controlPoints: true,
                });


                const layoutData = layout.layout(data);

                // console.log('layoutData', layoutData)


                gobalEvent.emit(uiEvent.updateDataProcessingDagNodeSize)
                graphRef.current.fromJSON(layoutData)



                updateAnimate()
                // showNodeStatus()

                // setTimeout(() => {
                //     showNodeStatus()
                //     gobalEvent.emit(uiEvent.updateDataProcessingDagNodeSize)
                // }, 2000)

                // setTimeout(() => {
                //     showNodeStatus()
                //     stopAnimate()
                // }, 3000)
            } else {
                updateAnimate()
                gobalEvent.emit(uiEvent.updateDataProcessingDagNodeData, data.nodeMap)
            }


            if (
                Object.keys(data.nodeStatusMap[T_PENDING] || {})?.length ||
                Object.keys(data.nodeStatusMap[T_RUNNING] || {})?.length
            ) {
                updateTimeoutIdRef.current = setTimeout(() => {
                    invokeInit(true)
                }, 3 * 1000)
            }

        }
    }, [updateAnimate])


    useEffect(() => {
        return () => {
            invokeCancelUpdateTimeoutIdRef()
        }
    }, [invokeCancelUpdateTimeoutIdRef])


    useEffect(() => {
        invokeInitGraph()
        invokeInit()
    }, [invokeInit, invokeInitGraph])


    useEffect(() => {
        return () => {
            graphRef.current?.dispose()
        }
    }, [])

    return (
        <div className="h-[100vh] w-[100vh]">
            <div id={containerRef.current}></div>
            <div className="fixed top-[20px] right-[20px]">
                <Button
                    type="primary"
                    onClick={onRedeployClick}
                >
                    重新运行
                </Button>
            </div>
        </div>
    )
}


export default Index