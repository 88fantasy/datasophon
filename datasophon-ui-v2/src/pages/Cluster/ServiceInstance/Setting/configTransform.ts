/**
 * 服务配置表单双向转换工具。
 *
 * 移植自 datasophon-ui/src/components/Common/CommonTemplate/utils.tsx。
 * 处理后端 ServiceConfig 与 ProForm 表单结构之间的数据互转，
 * 尤其是 multiple / multipleWithKey / multipleWithMap 三种复杂数组型字段。
 * 请勿简化这些转换——任何简化都可能导致对应服务配置数据静默损坏。
 */

type ConfigField = DATASOPHON.ConfigField;

const isPlainObject = (val: unknown): val is Record<string, unknown> => {
  return Boolean(val && typeof val === 'object' && !Array.isArray(val));
};

/** 判断一个配置项是否为「单值列表」类型（multiple 或 input+stringArray） */
export function invokeMapShowMultiply(item: ConfigField): boolean {
  const inputStringArray =
    item.type === 'input' && item.configType === 'stringArray';
  return ['multiple'].includes(item.type) || inputStringArray;
}

// ─── multiple/input+stringArray 类型互转 ────────────────────────────────────

/** 后端 → 表单：把逗号/分号分隔字符串或普通数组转为 [{value}] 结构 */
export function invokeReFormatMultiplyValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value
      .filter(Boolean)
      .map((v) => (isPlainObject(v) ? undefined : { value: v }))
      .filter(Boolean);
  } else if (typeof value === 'string') {
    return value.split(/,|;/).map((val) => ({ value: val }));
  }
  return value;
}

/** 表单 → 后端：把 [{value}] 结构还原为数组（stringArray 时 join 为逗号串） */
export function invokeFormatMultiplyValue(
  config: ConfigField,
  value: unknown,
): unknown {
  if (!Array.isArray(value)) return value;
  const result = (value as { value: unknown }[]).map((v) => v.value);
  if (config.type === 'input' && config.configType === 'stringArray') {
    return result.join(',');
  }
  return result;
}

// ─── multipleWithKey 类型互转 ─────────────────────────────────────────────

/** 后端 → 表单：把 [{key: value}] 对象数组转为 [{key, value}] 结构 */
export function invokeReMultipleWithKeyValue(value: unknown): unknown {
  if (!Array.isArray(value)) return value;
  return value.filter(isPlainObject).map((v) => {
    const key = Object.keys(v)[0];
    return { key, value: v[key] };
  });
}

/** 表单 → 后端：把 [{key, value}] 还原为 [{key: value}] 对象数组 */
export function invokeFormatMultipleWithKeyValue(value: unknown): unknown {
  if (!Array.isArray(value)) return value;
  return (value as { key: string; value: unknown }[])
    .filter(isPlainObject)
    .map(({ key, value: val }) => (key != null ? { [String(key)]: val } : null))
    .filter(Boolean);
}

// ─── multipleWithMap 类型互转 ─────────────────────────────────────────────

/** 后端 → 表单：把对象数组转为 [{items:[{key,value}]}] 嵌套结构 */
export function invokeReMultipleWithMapValue(value: unknown): unknown {
  if (!Array.isArray(value)) return value;
  return value.filter(isPlainObject).map((v) => ({
    items: Object.entries(v).map(([key, val]) => ({ key, value: val })),
  }));
}

/** 表单 → 后端：把 [{items:[{key,value}]}] 还原为对象数组 */
export function invokeFormatMultipleWithMapValue(value: unknown): unknown {
  if (!Array.isArray(value)) return value;
  return (value as { items: { key: string; value: unknown }[] }[])
    .filter((item) => isPlainObject(item) && Array.isArray(item.items))
    .map((item) => {
      const restored: Record<string, unknown> = {};
      for (const kv of item.items) {
        if (isPlainObject(kv) && kv.key !== undefined) {
          restored[String(kv.key)] = kv.value;
        }
      }
      return restored;
    });
}

// ─── 整体转换入口 ─────────────────────────────────────────────────────────

/**
 * 后端 → 表单：对所有复杂类型字段做展开转换，处理完的数据可直接用作 ProForm initialValues。
 * 必须在 getServiceConfig 拿到数据后立即调用。
 */
export function invokeHandleTemplateData(data: ConfigField[]): ConfigField[] {
  const cloned = structuredClone(data);
  cloned
    .filter((val) => !val.hidden)
    .forEach((val) => {
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
  return cloned;
}

/**
 * 表单 → 后端：把 ProForm values 合并回原始 templateData 中，准备提交给 saveServiceConfig。
 * originData 须为 invokeHandleTemplateData 转换前的原始后端数据（不要传转换后的版本）。
 */
export function invokeFormatTemplateData(
  originData: ConfigField[],
  values: Record<string, unknown>,
): ConfigField[] {
  const cloned = structuredClone(originData);
  cloned
    .filter((val) => !(!val.required && val.hidden))
    .forEach((val) => {
      const formVal = values[val.name];
      if (invokeMapShowMultiply(val)) {
        val.value = invokeFormatMultiplyValue(val, formVal);
      } else if (val.type === 'multipleWithKey') {
        val.value = invokeFormatMultipleWithKeyValue(formVal);
      } else if (val.type === 'multipleWithMap') {
        val.value = invokeFormatMultipleWithMapValue(formVal);
      } else {
        val.value = formVal;
      }
    });
  return cloned;
}
