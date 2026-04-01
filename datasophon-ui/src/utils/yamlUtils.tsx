import yaml from 'js-yaml';
import { merge } from 'lodash-es';
// 深度合并两个 YAML 对象
export function mergeYamlFiles(baseYaml, overrideYaml) {
    let res
    try {
        // 解析 YAML 为 JavaScript 对象
        const baseObj = yaml.load(baseYaml);
        const overrideObj = yaml.load(overrideYaml);

        // 深度合并（override 会覆盖 base 的同级属性）
        const mergedObj = merge({}, baseObj, overrideObj);

        // 转换回 YAML 格式
        res = yaml.dump(mergedObj, {
            indent: 2,
            lineWidth: -1, // 不自动换行
            noRefs: true,  // 避免循环引用
        });
    } catch (error) {
        console.error('YAML 合并失败:', error);
    }

    return res
}


export function parseYaml(yamlStr) {
    try {
        return yaml.load(yamlStr);
    } catch (error) {
        console.error('YAML 解析失败:', error);
        return null;
    }
}
