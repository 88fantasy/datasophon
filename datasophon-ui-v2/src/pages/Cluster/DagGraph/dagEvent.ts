/**
 * DAG 全局事件总线（精简版，仅保留 DagGraph 所需事件）。
 * 原版 datasophon-ui/src/utils/gobalEvent.ts 的精简迁移。
 */
import EventEmitter from 'eventemitter3';

let counter = 0;
const genId = () => `dag-ev-${++counter}`;

const dagEvent = new EventEmitter();

export const dagUiEvent = {
  updateNodeSize: genId(),
  updateNodeData: genId(),
};

export default dagEvent;
