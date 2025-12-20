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
        srvList = [],
        edge = []
    } = data


    const nodes = []
    const edges = []


    srvList.map(val => {
        val.id = String(val.id)
        const v = {
            id: val.id,
            shape: DataProcessingDagNode.shape,
            ports: invokeGenPort(val),
            data: val
        }
        nodes.push(v)
    })


    edge.map(val => {
        val.id = String(val.id)
        val.start = val.start && String(val.start)
        val.end = val.end && String(val.end)
        const v = {
            id: val.id,
            "shape": DataProcessingDagNode.edgeName,
            ...invokeGenSourceAndTarget(val.start, val.end)
        }


        edges.push(v)
    })

    console.log(nodes, edges)
    return {
        nodes,
        edges
    }

}
const Index = (props) => {

    const graphRef = useRef()
    const containerRef = useRef(invokeGenerateElId())

    const [state, setState] = useState({})







    const invokeInitGraph = useCallback(() => {

        const container = document.getElementById(containerRef.current);
        const wrapperEl = container.parentElement.parentElement
        const wrapperElStyle = window.getComputedStyle(container.parentElement)
        const graph: Graph = new Graph({
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



    const invokeInit = useCallback(async () => {
        const res = await axiosJsonPost(API.getDeployProgressDAG, {
            "clusterId": 1,
            "cmdIds": ["0100722958ec439ba2f0d865b98f18d7"]

        })


        if (res.code === 200) {

            let data = invokeTransferData(res.data)

            // data = {
            //     "nodes": [
            //         {
            //             "id": "node-0",
            //             "shape": DataProcessingDagNode.shape,
            //             "x": 0,
            //             "y": 100,
            //             "ports": [
            //                 {
            //                     "id": "node-0-out",
            //                     "group": "out"
            //                 }
            //             ],
            //             "data": {
            //                 "name": "数据输入_1",
            //                 "type": "INPUT",
            //                 "checkStatus": "sucess"
            //             }
            //         },
            //         {
            //             "id": "node-1",
            //             "shape": DataProcessingDagNode.shape,
            //             "x": 250,
            //             "y": 100,
            //             "ports": [
            //                 {
            //                     "id": "node-1-in",
            //                     "group": "in"
            //                 },
            //                 {
            //                     "id": "node-1-out",
            //                     "group": "out"
            //                 }
            //             ],
            //             "data": {
            //                 "name": "数据筛选_1",
            //                 "type": "FILTER"
            //             }
            //         },
            //         {
            //             "id": "node-2",
            //             "shape": DataProcessingDagNode.shape,
            //             "x": 250,
            //             "y": 200,
            //             "ports": [
            //                 {
            //                     "id": "node-2-out",
            //                     "group": "out"
            //                 }
            //             ],
            //             "data": {
            //                 "name": "数据输入_2",
            //                 "type": "INPUT"
            //             }
            //         },
            //         {
            //             "id": "node-3",
            //             "shape": DataProcessingDagNode.shape,
            //             "x": 500,
            //             "y": 100,
            //             "ports": [
            //                 {
            //                     "id": "node-3-in",
            //                     "group": "in"
            //                 },
            //                 {
            //                     "id": "node-3-out",
            //                     "group": "out"
            //                 }
            //             ],
            //             "data": {
            //                 "name": "数据连接_1",
            //                 "type": "JOIN"
            //             }
            //         },
            //         {
            //             "id": "node-4",
            //             "shape": DataProcessingDagNode.shape,
            //             "x": 750,
            //             "y": 100,
            //             "ports": [
            //                 {
            //                     "id": "node-4-in",
            //                     "group": "in"
            //                 }
            //             ],
            //             "data": {
            //                 "name": "数据输出_1",
            //                 "type": "OUTPUT"
            //             }
            //         }
            //     ],
            //     "edges": [
            //         {
            //             "id": "edge-0",
            //             "source": {
            //                 "cell": "node-0",
            //                 "port": "node-0-out"
            //             },
            //             "target": {
            //                 "cell": "node-1",
            //                 "port": "node-1-in"
            //             },
            //             "shape": "data-processing-curve",
            //             "zIndex": -1,
            //             "data": {
            //                 "source": "node-0",
            //                 "target": "node-1"
            //             }
            //         },
            //         {
            //             "id": "edge-1",
            //             "source": {
            //                 "cell": "node-2",
            //                 "port": "node-2-out"
            //             },
            //             "target": {
            //                 "cell": "node-3",
            //                 "port": "node-3-in"
            //             },
            //             "shape": "data-processing-curve",
            //             "zIndex": -1,
            //             "data": {
            //                 "source": "node-2",
            //                 "target": "node-3"
            //             }
            //         },
            //         {
            //             "id": "edge-2",
            //             "source": {
            //                 "cell": "node-1",
            //                 "port": "node-1-out"
            //             },
            //             "target": {
            //                 "cell": "node-3",
            //                 "port": "node-3-in"
            //             },
            //             "shape": "data-processing-curve",
            //             "zIndex": -1,
            //             "data": {
            //                 "source": "node-1",
            //                 "target": "node-3"
            //             }
            //         },
            //         {
            //             "id": "edge-3",
            //             "source": {
            //                 "cell": "node-3",
            //                 "port": "node-3-out"
            //             },
            //             "target": {
            //                 "cell": "node-4",
            //                 "port": "node-4-in"
            //             },
            //             "shape": "data-processing-curve",
            //             "zIndex": -1,
            //             "data": {
            //                 "source": "node-3",
            //                 "target": "node-4"
            //             }
            //         }
            //     ]
            // }
            graphRef.current.fromJSON(data)
            // const zoomOptions = {
            //     padding: {
            //         left: 10,
            //         right: 10,
            //     },
            // }
            // graphRef.current.zoomToFit(zoomOptions)

            setTimeout(() => {
                excuteAnimate()
                showNodeStatus()
                gobalEvent.emit(uiEvent.updateDataProcessingDagNodeSize)
            }, 2000)
            setTimeout(() => {
                showNodeStatus()
                stopAnimate()
            }, 3000)
        }
    }, [excuteAnimate, showNodeStatus, stopAnimate])


    useEffect(() => {
        invokeInitGraph()
        invokeInit()
    }, [invokeInit, invokeInitGraph])

    return (
        <div className="h-[40vh] w-full">
            <div id={containerRef.current}></div>
        </div>
    )
}


export default Index