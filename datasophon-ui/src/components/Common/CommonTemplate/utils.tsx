import { cloneDeep } from "lodash-es";

const isPlainObject = (val: unknown): val is Record<string, unknown> => {
    return Boolean(val && typeof val === 'object' && !Array.isArray(val));
};

export function invokeMapShowMultiply(item) {
    const inputStringArray =
        item.type === "input" && item.configType === "stringArray";
    return ["multiple"].includes(item.type) || inputStringArray;
}


export function invokeReFormatMultiplyValue(value) {
    if (Array.isArray(value)) {
        return value.filter(Boolean).map(v => isPlainObject(v) ? undefined : { value: v }).filter(Boolean);
    } else if (typeof value === 'string') {
        return value.split(/,|;/).map(val => ({ value: val }));
    }
    return value;
}

export function invokeFormatMultiplyValue(config, value) {
    if (!Array.isArray(value)) return value;

    const result = value.map(v => v.value);
    if (config.type === 'input' && config.configType === "stringArray") {
        return result.join(',');
    }
    return result;
}
export function invokeReMultipleWithKeyValue(value) {
    if (!Array.isArray(value)) return value;

    return value
        .filter(isPlainObject)
        .map(v => {
            const key = Object.keys(v)[0];
            return { key, value: v[key] };
        });
}

export function invokeFormatMultipleWithKeyValue(value) {
    if (!Array.isArray(value)) return value;

    return value
        .filter(isPlainObject)
        .map(({ key, value: val }) => (key != null ? { [String(key)]: val } : null))
        .filter(Boolean);
}

export function invokeReMultipleWithMapValue(value) {
    if (!Array.isArray(value)) return value;

    return value
        .filter(isPlainObject)
        .map(v => ({
            items: Object.entries(v).map(([key, val]) => ({ key, value: val }))
        }));
}

export function invokeFormatMultipleWithMapValue(value) {
    if (!Array.isArray(value)) return value;

    return value
        .filter(item => isPlainObject(item) && Array.isArray(item.items))
        .map(item => {
            const restored: Record<string, unknown> = {};
            for (const kv of item.items) {
                if (isPlainObject(kv) && kv.key !== undefined) {
                    restored[String(kv.key)] = kv.value;
                }
            }
            return restored;
        });
}



export function invokeHandleTemplateData(data) {
    data = cloneDeep(data)

    data
        .filter(val => !val.hidden)
        .forEach(val => {
            if (invokeMapShowMultiply(val)) {
                val.value = invokeReFormatMultiplyValue(val.value);
                val.defaultValue = invokeReFormatMultiplyValue(val.defaultValue);
            } else if (val.type === 'multipleWithKey') {
                val.value = invokeReMultipleWithKeyValue(val.value);
                val.defaultValue = invokeReMultipleWithKeyValue(val.defaultValue);
            } else if (val.type === 'multipleWithMap') {
                val.value = invokeReMultipleWithMapValue(val.value);
                val.defaultValue = invokeReMultipleWithMapValue(val.defaultValue);
            }
        });

    return data;
}


export function invokeFormatTemplateData(data, values) {
    data = cloneDeep(data)

    data
        .filter(val => !(!val.required && val.hidden))
        .forEach(val => {
            const valuesVal = values[val.name];
            if (invokeMapShowMultiply(val)) {
                val.value = invokeFormatMultiplyValue(val, valuesVal);
            } else if (val.type === 'multipleWithKey') {
                val.value = invokeFormatMultipleWithKeyValue(valuesVal);
            } else if (val.type === 'multipleWithMap') {
                val.value = invokeFormatMultipleWithMapValue(valuesVal);
            } else {
                val.value = valuesVal;
            }
        });

    return data;
}