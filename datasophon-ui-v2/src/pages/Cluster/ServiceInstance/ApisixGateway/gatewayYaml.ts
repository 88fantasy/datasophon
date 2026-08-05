import yaml from 'js-yaml';

/**
 * `apisixGatewayYaml` 真相源的结构化投影。图形化视图只读写 routes/upstreams/global_rules，
 * 其余顶层段（如用户在代码视图写的 consumers/ssls）与单条记录上的未知字段原样保留，
 * dump 时自然带出——这是约束 1（图形化只投影它认识的部分）。
 */
export interface ApisixRoute {
  id: number | string;
  uri?: string;
  upstream_id?: number | string;
  plugins?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface ApisixUpstream {
  id: number | string;
  type?: string;
  nodes?: Record<string, number>;
  [key: string]: unknown;
}

export interface ApisixGlobalRule {
  id: number | string;
  plugins?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface ApisixGatewayDoc {
  upstreams?: ApisixUpstream[];
  routes?: ApisixRoute[];
  global_rules?: ApisixGlobalRule[];
  [key: string]: unknown;
}

/** 内置 global_rules：id 1 = prometheus，id 2 = opentelemetry，后端 POST 校验强制要求两者都在 */
export const BUILTIN_GLOBAL_RULE_IDS = ['1', '2'];

export function isBuiltinGlobalRule(rule: { id: number | string }): boolean {
  return BUILTIN_GLOBAL_RULE_IDS.includes(String(rule.id));
}

/** 顶层非 map（如空文档、纯数组、纯标量）时返回空 map，不当作解析失败 */
export function loadGatewayYaml(text: string): ApisixGatewayDoc {
  const doc = yaml.load(text);
  if (doc == null) {
    return {};
  }
  if (typeof doc !== 'object' || Array.isArray(doc)) {
    throw new Error('YAML 顶层必须是一个 map');
  }
  return doc as ApisixGatewayDoc;
}

export function dumpGatewayYaml(doc: ApisixGatewayDoc): string {
  return yaml.dump(doc, { indent: 2, lineWidth: -1, noRefs: true });
}

/**
 * 内置规则不可删——被删除的调用方应先用此函数过滤，而不是直接对 global_rules 数组 splice。
 */
export function removeGlobalRule(
  rules: ApisixGlobalRule[],
  id: number | string,
): ApisixGlobalRule[] {
  return rules.filter(
    (rule) => isBuiltinGlobalRule(rule) || String(rule.id) !== String(id),
  );
}

/** 表单提交合并语义：{...originalItem, ...formValues}，保留表单不认识的字段（约束 1） */
export function upsertById<T extends { id: number | string }>(
  list: T[],
  updated: T,
): T[] {
  const idx = list.findIndex((item) => String(item.id) === String(updated.id));
  if (idx === -1) {
    return [...list, updated];
  }
  const next = [...list];
  next[idx] = { ...list[idx], ...updated };
  return next;
}

/** id 唯一性校验：返回重复出现的 id 列表（去重） */
export function findDuplicateIds(items: { id: number | string }[]): string[] {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const item of items) {
    const key = String(item.id);
    if (seen.has(key)) {
      duplicates.add(key);
    }
    seen.add(key);
  }
  return [...duplicates];
}

/** upstream 引用完整性校验：返回引用了不存在 upstream 的 route 的 uri/id 列表 */
export function findDanglingUpstreamRefs(doc: ApisixGatewayDoc): string[] {
  const upstreamIds = new Set((doc.upstreams ?? []).map((u) => String(u.id)));
  return (doc.routes ?? [])
    .filter(
      (route) =>
        route.upstream_id != null && !upstreamIds.has(String(route.upstream_id)),
    )
    .map((route) => String(route.uri ?? route.id));
}

/**
 * 注释检测（约束 2）：非严格 YAML 词法分析，逐行剥离引号内容后看是否还含 `#`。
 * 用途仅是决定要不要弹「切换将丢失注释」的二次确认，误判的代价很低，
 * 不值得为此引入完整的 YAML tokenizer。
 */
export function hasComments(text: string): boolean {
  return text.split('\n').some((line) => {
    const withoutStrings = line.replace(/'[^']*'|"[^"]*"/g, '');
    return withoutStrings.includes('#');
  });
}
