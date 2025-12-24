import { message, Modal, type ModalFuncProps } from "antd";
import enquireJs from "enquire.js";
import { isNull, isUndefined } from "lodash-es";
import { codeMessage } from "../api/interceptors";

export function isDef(v) {
  return v !== undefined && v !== null;
}

/**
 * Remove an item from an array.
 */
export function remove(arr, item) {
  if (arr.length) {
    const index = arr.indexOf(item);
    if (index > -1) {
      return arr.splice(index, 1);
    }
  }
}

export function isRegExp(v) {
  return _toString.call(v) === "[object RegExp]";
}

export function enquireScreen(call) {
  const handler = {
    match: function () {
      call && call(true);
    },
    unmatch: function () {
      call && call(false);
    },
  };
  enquireJs.register("only screen and (max-width: 767.99px)", handler);
}

export function showComfirmModal(options?: ModalFuncProps) {
  return new Promise((resolve) => {
    if (!options) {
      options = {};
    }

    const bakOk = options.onOk;

    const bakOnCancel = options.onCancel;

    options.onOk = () => {
      if (bakOk) {
        bakOk();
      }
      resolve(true);
    };

    options.onCancel = () => {
      if (bakOnCancel) {
        bakOnCancel();
      }
      resolve(false);
    };

    return Modal.confirm({
      title: "温馨提示",
      content: "是否执行该操作？",
      cancelText: "取消",
      okText: "确认",
      okType: "primary",
      ...options,
    });
  });
}

export const showMsgAfferRequest = (res) => {
  if (res.code === 200) {
    message.success(res.message || res.msg || "执行成功");
  } else {
    if (res.msg && res.msg.indexOf(codeMessage[res.code]) > -1) {
      return;
    }

    message.error(res.message || res.msg || "执行失败");
  }
};

export const invokeGenerateElId = () => {
  if (!window.elId) {
    window.elId = 1;
  }

  window.elId += 1;

  return `elid-${window.elId}`;
};

export const requireRules = [{ required: true }];

export const isEmpty = (res) => {
  return isNull(res) || isUndefined(res) || res === "";
};

const _toString = Object.prototype.toString;
