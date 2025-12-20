/*
 * @Author: Rhymedys/Rhymedys@gmail.com
 * @Date: 2019-03-17 16:51:25
 * @Last Modified by: Rhymedys
 * @Last Modified time: 2021-03-23 15:55:14
 */

import EventEmitter from "eventemitter3";
import { invokeGenerateElId } from "./util";

const gobalEvent = new EventEmitter();

const uiEvent = {
  updateDataProcessingDagNodeSize: invokeGenerateElId()
};

export { uiEvent };
export default gobalEvent;
