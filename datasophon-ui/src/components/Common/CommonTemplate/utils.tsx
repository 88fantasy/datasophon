import { cloneDeep } from "lodash-es";

export function invokeMapShowMultiply(item) {
    const inputStringArray =
        item.type === "input" && item.configType === "stringArray";
    return ["multiple"].includes(item.type) || inputStringArray;
}


export function invokeReFormatMultiplyValue(value) {
    if (Array.isArray(value)) {
        value = value.map(v => {
            if (!/Object/.test(Object.prototype.toString.call(v))) {
                return {
                    value: v
                }
            }
        })
    } else if (typeof value === 'string') {
        value = value.split(/,|;/).map(val => {
            return {
                value: val
            }
        })
    }


    return value
}

export function invokeFormatMultiplyValue(config, value) {
    if (Array.isArray(value)) {
        value = value.map(v => {
            return v.value
        })
        if (config.type === 'input' && config.configType === "stringArray") {
            value = value.join(',')
        }

    }

    return value
}
export function invokeReMultipleWithKeyValue(value) {
    if (Array.isArray(value)) {
        value = value.map(v => {
            if (/Object/.test(Object.prototype.toString.call(v))) {
                const arr = Object.keys(v)
                const key = arr[0]
                return {
                    key,
                    value: v[key]
                }
            }
        })
    }


    return value

}

export function invokeFormatMultipleWithKeyValue(value) {

    if (Array.isArray(value)) {
        value = value.map((item) => {
            if (
                item &&
                typeof item === 'object' &&
                !Array.isArray(item)
            ) {
                const { key, value: val } = item;
                if (key != null) {
                    return { [key]: val };
                }
            }
            return item;
        });
    }
    return value;
}

export function invokeReMultipleWithMapValue(value) {
    if (Array.isArray(value)) {
        value = value.map(v => {
            if (/Object/.test(Object.prototype.toString.call(v))) {
                const res = []
                for (const key in v) {
                    res.push({
                        key,
                        value: v[key]
                    })
                }
                return {
                    items: res
                }
            }
        })
    }

    return value

}

export function invokeFormatMultipleWithMapValue(value) {
    if (Array.isArray(value)) {
        return value.map(item => {
            if (
                item &&
                typeof item === 'object' &&
                !Array.isArray(item) &&
                Array.isArray(item.items)
            ) {
                const restored = {};
                for (const kv of item.items) {
                    // 确保 kv 是 { key, value } 结构
                    if (kv && typeof kv === 'object' && kv.key !== undefined) {
                        restored[kv.key] = kv.value;
                    }
                }
                return restored;
            }
            // 如果不符合结构，原样返回（或可选择过滤/报错）
            return item;
        });
    }
    // 非数组直接返回
    return value;
}



export function invokeHandleTemplateData(data) {
    data = cloneDeep(data)

    data
        .filter(val => !val.hidden)
        .map(val => {
            if (invokeMapShowMultiply(val)) {
                val.value = invokeReFormatMultiplyValue(val.value)
                val.defaultValue = invokeReFormatMultiplyValue(val.defaultValue)
            } else if (val.type === 'multipleWithKey') {
                val.value = invokeReMultipleWithKeyValue(val.value)
                val.defaultValue = invokeReMultipleWithKeyValue(val.defaultValue)
            } else if (val.type === 'multipleWithMap') {
                val.value = invokeReMultipleWithMapValue(val.value)
                val.defaultValue = invokeReMultipleWithMapValue(val.defaultValue)
            }
        })

    return data
}


export function invokeFormatTemplateData(data, values) {
    data = cloneDeep(data)

    data
        .filter(val => !(!val.required && val.hidden))
        .map(val => {
            if (invokeMapShowMultiply(val)) {
                val.value = invokeFormatMultiplyValue(val, values[val.name])
            } else if (val.type === 'multipleWithKey') {
                val.value = invokeFormatMultipleWithKeyValue(val.value)
            } else if (val.type === 'multipleWithMap') {
                val.value = invokeFormatMultipleWithMapValue(val.value)
            } else {
                val.value = values[val.name]
            }
        })

    return data
}