export const meta = {
  name: 'component-dashboard-survey',
  description: '并行调研 25 个组件的 Prometheus 数据源与 Grafana 看板并合并为选型报告',
  phases: [
    { title: 'Survey', detail: '每组件一个 agent:查官网确认原生 /metrics + grafana.com 候选看板 + 加权打分' },
    { title: 'Synthesize', detail: '合并 25 份结果为总览表 + 详情报告' },
  ],
}

const COMPONENTS = args
if (!Array.isArray(COMPONENTS) || COMPONENTS.length === 0) {
  throw new Error('args 必须是 manifest 组件数组(非空)')
}

const COMPONENT_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['component', 'version', 'group', 'nativePrometheus', 'exporterFallback', 'dataSource', 'candidates', 'recommendation', 'tier'],
  properties: {
    component: { type: 'string' },
    version: { type: 'string' },
    group: { type: 'string' },
    nativePrometheus: {
      type: 'object',
      additionalProperties: false,
      required: ['supported', 'endpoint', 'docUrl'],
      properties: {
        supported: { type: 'boolean' },
        sinceVersion: { type: 'string' },
        endpoint: { type: 'string' },
        port: { type: ['string', 'integer'] },
        docUrl: { type: 'string' },
        notes: { type: 'string' },
      },
    },
    exporterFallback: {
      type: 'object',
      additionalProperties: false,
      required: ['needed'],
      properties: {
        needed: { type: 'boolean' },
        name: { type: ['string', 'null'] },
        repoUrl: { type: ['string', 'null'] },
      },
    },
    dataSource: { type: 'string', enum: ['native', 'exporter', 'none'] },
    candidates: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['name', 'source', 'url', 'scores', 'total'],
        properties: {
          name: { type: 'string' },
          source: { type: 'string' },
          url: { type: 'string' },
          grafanaId: { type: ['integer', 'string', 'null'] },
          downloads: { type: ['integer', 'null'] },
          rating: { type: ['number', 'null'] },
          datasource: { type: ['string', 'null'] },
          lastUpdate: { type: ['string', 'null'] },
          scores: {
            type: 'object',
            additionalProperties: false,
            required: ['heat', 'datasourceMatch', 'promqlPortability', 'goldenSignals'],
            properties: {
              heat: { type: 'number' },
              datasourceMatch: { type: 'number' },
              promqlPortability: { type: 'number' },
              goldenSignals: { type: 'number' },
            },
          },
          total: { type: 'number' },
        },
      },
    },
    recommendation: {
      type: 'object',
      additionalProperties: false,
      required: ['pick', 'reason'],
      properties: { pick: { type: 'string' }, reason: { type: 'string' } },
    },
    tier: { type: 'string', enum: ['🟢可直接做', '🟡需自建', '🔴缺数据源'] },
  },
}

function buildPrompt(c) {
  return [
    `你是监控调研专家。调研组件 ${c.component}(版本 ${c.version},分组 ${c.group})。`,
    `官网线索:${c.docHint || '(自行检索官网)'}`,
    '',
    '第一步 数据源探测(原生优先):',
    `用 WebSearch/WebFetch 查 ${c.component} ${c.version} 官方文档,确认该版本是否原生暴露 Prometheus /metrics 端点。`,
    '记录 nativePrometheus.supported(布尔)、sinceVersion、endpoint(路径)、port、docUrl(证据链接)、notes。',
    '仅当原生不支持时,才找成熟 exporter(exporterFallback.needed=true + name + repoUrl);否则 needed=false、name/repoUrl 置 null。',
    'dataSource 取 native(用原生端点)/ exporter(需 exporter)/ none(都没有)。',
    '',
    '第二步 看板检索:',
    '主源用 grafana.com 官方 API(WebFetch):',
    `https://grafana.com/api/dashboards?search=${encodeURIComponent(c.component)}&orderBy=downloads&direction=desc`,
    '取 2-3 个候选,记录 name、source(grafana.com|github|official)、url、grafanaId、downloads、rating、datasource、lastUpdate。',
    '辅源:GitHub、组件官方仓库自带 dashboard。无候选则 candidates 为空数组。',
    '',
    '第三步 加权打分(每个候选,每项 = 归一分(0~1) × 权重,即已是加权贡献):',
    'scores.heat(权重0.3)= 按 grafana.com 下载量/评分归一后 ×0.3;',
    'scores.datasourceMatch(0.3)= 看板指标名是否对应第一步确定的数据源(走原生端点的组件,看板须基于原生指标名;基于旧 exporter 指标名则低分)归一后 ×0.3;',
    'scores.promqlPortability(0.2)= PromQL 是否干净易移植 归一后 ×0.2;',
    'scores.goldenSignals(0.2)= 是否覆盖延迟/流量/错误/饱和度 归一后 ×0.2;',
    'total = 四项之和(范围 0~1)。',
    '',
    '第四步 分级与推荐:',
    'tier:🟢可直接做(有数据源 + 有优质看板) / 🟡需自建(有数据源但无匹配看板) / 🔴缺数据源。',
    'recommendation.pick = 推荐看板名(🟡 填"需自建",🔴 填"缺数据源");reason = 理由(引用分项得分)。',
    '',
    `component/version/group 必须原样回填为 "${c.component}" / "${c.version}" / "${c.group}"。`,
    '严格按结构化 schema 返回,所有 URL 必须真实可访问。',
  ].join('\n')
}

phase('Survey')
const first = await parallel(COMPONENTS.map((c) => () =>
  agent(buildPrompt(c), { label: `survey:${c.component}`, phase: 'Survey', schema: COMPONENT_SCHEMA })
))

// 单组件重试:对失败(null)的再跑一次,不连累已成功的
const finalResults = await Promise.all(first.map(async (r, i) => {
  if (r) return r
  log(`重试组件:${COMPONENTS[i].component}`)
  return await agent(buildPrompt(COMPONENTS[i]), { label: `retry:${COMPONENTS[i].component}`, phase: 'Survey', schema: COMPONENT_SCHEMA })
}))

const good = finalResults.filter(Boolean)
const failed = COMPONENTS.filter((c, i) => !finalResults[i]).map((c) => c.component)

phase('Synthesize')
const report = await agent([
  '把下列组件监控调研结果合并为一份 Markdown 报告。',
  '',
  '报告结构:',
  `1) 标题「组件监控看板调研报告」+ 一行生成说明(共 ${good.length} 个组件)。`,
  '2) 总览表,列:组件 | 版本 | 数据源方式 | 候选数 | 分级 | 推荐看板 | 总分。',
  '   数据源方式:native 显示"原生 {endpoint}:{port}";exporter 显示"exporter {name}";none 显示"无"。',
  '   总分:取 recommendation 对应候选看板的 total;🟡/🔴 留空。',
  '3) 每组件详情(按分组归类):',
  '   - 数据源结论(supported / endpoint / port / sinceVersion / docUrl 证据链接);',
  '   - 候选看板列表(名称 / 链接 / Grafana ID / 下载量 / 数据源 / 更新时间);',
  '   - 分项打分表(heat / datasourceMatch / promqlPortability / goldenSignals / total);',
  '   - 推荐结论与理由。',
  '',
  '只输出 Markdown 正文,不要任何额外解释或代码块包裹。',
  '数据如下(JSON):',
  JSON.stringify(good),
].join('\n'), { label: 'synthesize', phase: 'Synthesize' })

return { results: good, report, failed }
