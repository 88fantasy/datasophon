import React from 'react';

export const reactComponentChecker = {
    // 检查是否为 React 元素
    isElement(obj) {
        return React.isValidElement(obj);
    },

    // 检查是否为函数组件
    isFunctionComponent(obj) {
        return typeof obj === 'function' &&
            obj.prototype &&
            !obj.prototype.isReactComponent;
    },

    // 检查是否为类组件
    isClassComponent(obj) {
        return typeof obj === 'function' &&
            obj.prototype &&
            obj.prototype.isReactComponent;
    },

    // 检查是否为 React 组件（函数或类）
    isComponent(obj) {
        return typeof obj === 'function' || React.isValidElement(obj);
    },
    // 获取组件类型
    getComponentType(obj) {
        if (React.isValidElement(obj)) {
            return 'element';
        }
        if (typeof obj === 'function') {
            if (obj.prototype && obj.prototype.isReactComponent) {
                return 'class';
            }
            return 'function';
        }
        return 'unknown';
    }
};
