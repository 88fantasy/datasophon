import yaml from 'js-yaml';

/** 递归深合并两个普通对象（override 中的标量/数组直接覆盖 base 的同名字段） */
function deepMerge(base: Record<string, unknown>, override: Record<string, unknown>) {
  const result = structuredClone(base) as Record<string, unknown>;
  for (const key of Object.keys(override)) {
    const bv = result[key];
    const ov = override[key];
    if (
      bv !== null &&
      ov !== null &&
      typeof bv === 'object' &&
      typeof ov === 'object' &&
      !Array.isArray(bv) &&
      !Array.isArray(ov)
    ) {
      result[key] = deepMerge(bv as Record<string, unknown>, ov as Record<string, unknown>);
    } else {
      result[key] = ov;
    }
  }
  return result;
}

/**
 * 将 overrideYaml 深合并到 baseYaml 中，返回合并后的 YAML 文本。
 *
 * - override 中相同 key 会覆盖 base 中的对应值（与 lodash.merge 语义一致）。
 * - 若任一参数为空/非 YAML 对象，则直接返回 baseYaml。
 * - 解析/合并失败时打印错误，回退返回 baseYaml。
 */
export function mergeYamlFiles(baseYaml: string, overrideYaml: string): string {
  if (!baseYaml) return baseYaml;
  if (!overrideYaml || overrideYaml.trim() === '') return baseYaml;

  try {
    const baseObj = yaml.load(baseYaml);
    const overrideObj = yaml.load(overrideYaml);

    if (
      typeof baseObj !== 'object' ||
      baseObj === null ||
      typeof overrideObj !== 'object' ||
      overrideObj === null
    ) {
      return baseYaml;
    }

    const merged = deepMerge(
      baseObj as Record<string, unknown>,
      overrideObj as Record<string, unknown>,
    );

    return yaml.dump(merged, { indent: 2, lineWidth: -1, noRefs: true });
  } catch (error) {
    console.error('YAML 合并失败:', error);
    return baseYaml;
  }
}
