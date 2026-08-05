import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getApisixGateway, saveApisixGateway } from '@/services/service';
import ApisixGatewayPanel from './index';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) => id,
  }),
}));

// @ant-design/pro-components 直接 import 在这套 vitest(happy-dom + ESM) 组合下会报
// "exports is not defined in ES module scope"，仓库既有测试（如 History.test.tsx）也用 mock 绕过。
// index.tsx 只用到 ProCard 做外层容器，透传 children 即可。
vi.mock('@ant-design/pro-components', () => ({
  ProCard: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('@monaco-editor/react', () => ({
  default: ({
    value,
    onChange,
    options,
  }: {
    value: string;
    onChange?: (v: string) => void;
    options?: { readOnly?: boolean };
  }) => (
    <textarea
      data-testid={options?.readOnly ? 'editor-readonly' : 'editor'}
      value={value}
      readOnly={!!options?.readOnly}
      onChange={(e) => onChange?.(e.target.value)}
    />
  ),
}));

vi.mock('./GraphicView', () => ({
  default: ({
    doc,
    onChange,
  }: {
    doc: Record<string, unknown>;
    onChange: (next: Record<string, unknown>) => void;
  }) => (
    <div>
      <div data-testid="graphic-view" />
      <button
        type="button"
        onClick={() =>
          onChange({
            ...doc,
            routes: [
              ...((doc.routes as unknown[]) ?? []),
              { id: 999, uri: '/added' },
            ],
          })
        }
      >
        mutate-doc
      </button>
    </div>
  ),
}));

vi.mock('@/services/service', () => ({
  getApisixGateway: vi.fn(),
  saveApisixGateway: vi.fn(),
}));

const CLUSTER_ID = 1;
const INSTANCE_ID = 10;

const WITH_COMMENTS_TEXT =
  'routes:\n  - id: 1\n    uri: /get\n# a real comment\nconsumers:\n  - username: alice\n';

function mockGetResponse(text: string) {
  vi.mocked(getApisixGateway).mockResolvedValue({
    data: { gatewayYaml: text, managedSuffix: '#END\n', roles: [] },
  } as never);
}

describe('ApisixGatewayPanel view switch state machine', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(saveApisixGateway).mockResolvedValue({ data: [] } as never);
  });

  it('code view is usable standalone: loads text and saves it verbatim', async () => {
    mockGetResponse('routes:\n  - id: 1\n    uri: /get\n');
    render(<ApisixGatewayPanel clusterId={CLUSTER_ID} instanceId={INSTANCE_ID} />);

    const editor = await screen.findByTestId('editor');
    expect(editor).toHaveValue('routes:\n  - id: 1\n    uri: /get\n');

    fireEvent.change(editor, { target: { value: 'routes:\n  - id: 1\n    uri: /new\n' } });
    fireEvent.click(screen.getByText('pages.apisixGateway.save'));

    await waitFor(() =>
      expect(saveApisixGateway).toHaveBeenCalledWith(
        CLUSTER_ID,
        INSTANCE_ID,
        'routes:\n  - id: 1\n    uri: /new\n',
      ),
    );
  });

  it('code -> graphic: unparsable YAML stays on code view and does not crash', async () => {
    mockGetResponse('not: [valid');
    render(<ApisixGatewayPanel clusterId={CLUSTER_ID} instanceId={INSTANCE_ID} />);
    await screen.findByTestId('editor');

    fireEvent.mouseDown(screen.getByText('pages.apisixGateway.view.graphic'));
    fireEvent.click(screen.getByText('pages.apisixGateway.view.graphic'));

    expect(screen.queryByTestId('graphic-view')).not.toBeInTheDocument();
    expect(screen.getByTestId('editor')).toBeInTheDocument();
  });

  it('code -> graphic: valid YAML switches successfully', async () => {
    mockGetResponse('routes:\n  - id: 1\n    uri: /get\n');
    render(<ApisixGatewayPanel clusterId={CLUSTER_ID} instanceId={INSTANCE_ID} />);
    await screen.findByTestId('editor');

    fireEvent.click(screen.getByText('pages.apisixGateway.view.graphic'));

    await screen.findByTestId('graphic-view');
    expect(screen.queryByTestId('editor')).not.toBeInTheDocument();
  });

  it('graphic -> code (unchanged): reuses the original text verbatim, preserving comments/formatting', async () => {
    mockGetResponse(WITH_COMMENTS_TEXT);
    render(<ApisixGatewayPanel clusterId={CLUSTER_ID} instanceId={INSTANCE_ID} />);
    await screen.findByTestId('editor');

    fireEvent.click(screen.getByText('pages.apisixGateway.view.graphic'));
    await screen.findByTestId('graphic-view');

    fireEvent.click(screen.getByText('pages.apisixGateway.view.code'));

    const editor = await screen.findByTestId('editor');
    expect(editor).toHaveValue(WITH_COMMENTS_TEXT);
  });

  it('graphic -> code (changed, no comments): dumps doc without confirmation', async () => {
    mockGetResponse('routes:\n  - id: 1\n    uri: /get\n');
    render(<ApisixGatewayPanel clusterId={CLUSTER_ID} instanceId={INSTANCE_ID} />);
    await screen.findByTestId('editor');

    fireEvent.click(screen.getByText('pages.apisixGateway.view.graphic'));
    await screen.findByTestId('graphic-view');
    fireEvent.click(screen.getByText('mutate-doc'));

    fireEvent.click(screen.getByText('pages.apisixGateway.view.code'));

    const editor = await screen.findByTestId('editor');
    expect((editor as HTMLTextAreaElement).value).toContain('/added');
    expect(
      screen.queryByText('pages.apisixGateway.confirm.commentsLostTitle'),
    ).not.toBeInTheDocument();
  });

  it('graphic -> code (changed, has comments): asks for confirmation before dumping', async () => {
    mockGetResponse(WITH_COMMENTS_TEXT);
    render(<ApisixGatewayPanel clusterId={CLUSTER_ID} instanceId={INSTANCE_ID} />);
    await screen.findByTestId('editor');

    fireEvent.click(screen.getByText('pages.apisixGateway.view.graphic'));
    await screen.findByTestId('graphic-view');
    fireEvent.click(screen.getByText('mutate-doc'));

    fireEvent.click(screen.getByText('pages.apisixGateway.view.code'));

    await waitFor(() =>
      expect(
        screen.getAllByText('pages.apisixGateway.confirm.commentsLostTitle').length,
      ).toBeGreaterThan(0),
    );
    // 确认前仍停在图形化视图，代码尚未被覆盖
    expect(screen.getByTestId('graphic-view')).toBeInTheDocument();

    const okButton = screen
      .getAllByRole('button')
      .find((btn) => btn.textContent === 'OK') as HTMLElement;
    fireEvent.click(okButton);

    const editor = await screen.findByTestId('editor');
    expect((editor as HTMLTextAreaElement).value).toContain('/added');
    expect((editor as HTMLTextAreaElement).value).not.toContain('a real comment');
  });

  it('saves dump(doc) when currently on the graphic view', async () => {
    mockGetResponse('routes:\n  - id: 1\n    uri: /get\n');
    render(<ApisixGatewayPanel clusterId={CLUSTER_ID} instanceId={INSTANCE_ID} />);
    await screen.findByTestId('editor');

    fireEvent.click(screen.getByText('pages.apisixGateway.view.graphic'));
    await screen.findByTestId('graphic-view');
    fireEvent.click(screen.getByText('mutate-doc'));

    fireEvent.click(screen.getByText('pages.apisixGateway.save'));

    await waitFor(() => expect(saveApisixGateway).toHaveBeenCalled());
    const payload = vi.mocked(saveApisixGateway).mock.calls[0][2];
    expect(payload).toContain('/added');
  });
});
