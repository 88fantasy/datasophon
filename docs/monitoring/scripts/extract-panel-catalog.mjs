// 用法: node docs/monitoring/scripts/extract-panel-catalog.mjs <input-dashboard.json> <COMPONENT>
// 产出: docs/monitoring/panel-catalog/<COMPONENT>.json
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'

const [, , inputPath, component] = process.argv
if (!inputPath || !component) {
  console.error('用法: node extract-panel-catalog.mjs <input-dashboard.json> <COMPONENT>')
  process.exit(1)
}

const dash = JSON.parse(readFileSync(inputPath, 'utf8'))

// 兼容旧版(v4/v5) rows[].panels 格式与新版顶层 panels 格式
// 旧版: dash.rows[].panels；新版: dash.panels(含 type=row 嵌套子面板)
function getRootPanels(dash) {
  if (Array.isArray(dash.rows) && dash.rows.length > 0) {
    return dash.rows.flatMap((r) => r.panels || [])
  }
  return dash.panels || []
}

// Grafana 看板的 panels 可能嵌套在 row 内,递归展开
function flattenPanels(panels) {
  const out = []
  for (const p of panels || []) {
    if (p.type === 'row' && Array.isArray(p.panels)) {
      out.push(...flattenPanels(p.panels))
    } else {
      out.push(p)
    }
  }
  return out
}

const catalog = flattenPanels(getRootPanels(dash))
  .filter((p) => p.type !== 'row' && p.type !== 'text')
  .map((p) => {
    const promql = (p.targets || []).map((t) => t.expr).filter(Boolean)
    // 兼容新版 fieldConfig 与旧版 yaxes / 阈值结构
    const unit = p.fieldConfig?.defaults?.unit ?? p.yaxes?.[0]?.format ?? null
    const thresholds = p.fieldConfig?.defaults?.thresholds?.steps ?? p.thresholds ?? null
    const legend = p.options?.legend ?? p.legend ?? null
    return {
      title: p.title || '(untitled)',
      promql,
      unit,
      chartType: p.type || null,
      thresholds,
      legend,
    }
  })
  .filter((p) => p.promql.length > 0)

const outPath = `docs/monitoring/panel-catalog/${component}.json`
mkdirSync(dirname(outPath), { recursive: true })
writeFileSync(outPath, JSON.stringify(catalog, null, 2), 'utf8')
console.log(`✓ ${component}: ${catalog.length} 个面板 → ${outPath}`)
