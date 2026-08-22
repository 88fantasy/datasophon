import { fireEvent, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import GlobalRuleTable from './GlobalRuleTable';
import type { ApisixGatewayDoc } from './gatewayYaml';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) => id,
  }),
}));

// @ant-design/pro-components 在这个 vitest(happy-dom + ESM) 组合下直接 import 会报
// "exports is not defined in ES module scope"（仓库既有测试如 History.test.tsx 也是这么绕过的），
// 这里给一个足够真实的 ProTable mock：按 columns 对 dataSource 逐行渲染，
// 保证「操作」列里的 <a> 链接是真实可点击的 DOM 节点。
vi.mock('@ant-design/pro-components', () => ({
  ProTable: ({
    columns,
    dataSource,
    toolBarRender,
  }: {
    columns: Array<{
      dataIndex?: string;
      render?: (v: unknown, r: any) => ReactNode;
    }>;
    dataSource: any[];
    toolBarRender?: () => ReactNode;
  }) => (
    <div>
      {toolBarRender?.()}
      <table>
        <tbody>
          {dataSource.map((record, i) => (
            <tr key={record.id ?? i}>
              {columns.map((col, j) => (
                <td key={col.dataIndex ?? j}>
                  {col.render
                    ? col.render(
                        col.dataIndex ? record[col.dataIndex] : undefined,
                        record,
                      )
                    : String(record[col.dataIndex ?? ''] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ),
  ModalForm: ({ open, children }: { open: boolean; children: ReactNode }) =>
    open ? <div>{children}</div> : null,
  ProFormDigit: () => null,
  ProFormText: () => null,
  ProFormTextArea: () => null,
  ProFormSelect: () => null,
}));

const DOC: ApisixGatewayDoc = {
  global_rules: [
    { id: 1, plugins: { prometheus: { prefer_name: true } } },
    { id: 2, plugins: { opentelemetry: {} } },
    { id: 3, plugins: { 'limit-count': {} } },
  ],
};

describe('GlobalRuleTable builtin rule protection', () => {
  it('marks id 1/2 as builtin and hides edit/delete actions for them', () => {
    render(<GlobalRuleTable doc={DOC} onChange={vi.fn()} />);

    const builtinTags = screen.getAllByText(
      'pages.apisixGateway.globalRule.builtin',
    );
    expect(builtinTags).toHaveLength(2);

    // 非内置规则(id 3)仍有编辑/删除入口
    expect(screen.getAllByText('删除')).toHaveLength(1);
    expect(screen.getAllByText('编辑')).toHaveLength(1);
  });

  it('deleting the non-builtin rule keeps both builtin rules intact', () => {
    const onChange = vi.fn();
    render(<GlobalRuleTable doc={DOC} onChange={onChange} />);

    fireEvent.click(screen.getByText('删除'));

    expect(onChange).toHaveBeenCalledWith({
      ...DOC,
      global_rules: [
        { id: 1, plugins: { prometheus: { prefer_name: true } } },
        { id: 2, plugins: { opentelemetry: {} } },
      ],
    });
  });
});
