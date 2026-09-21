import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { T_RUNNING } from './dagStatus';
import DagGraphPage from './index';

const mocks = vi.hoisted(() => ({
  getDagGraph: vi.fn(),
  redeployDag: vi.fn(),
  fromJSON: vi.fn(),
  dispose: vi.fn(),
  emit: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useParams: () => ({ clusterId: '1', dagId: 'task-1' }),
  request: vi.fn(),
}));
vi.mock('@/services/dag', () => mocks);
vi.mock('@antv/x6', () => ({
  IS_SAFARI: false,
  Graph: class {
    fromJSON = mocks.fromJSON;
    dispose = mocks.dispose;
    getEdges() {
      return [];
    }
  },
}));
vi.mock('./DataProcessingDagNode', () => ({
  default: { invokeInit: vi.fn(), shape: 'node', edgeName: 'edge' },
}));
vi.mock('./antvUtils', () => ({
  invokeGenPort: () => ({}),
  invokeGenSourceAndTarget: () => ({}),
}));
vi.mock('./dagEvent', () => ({
  default: { emit: mocks.emit },
  dagUiEvent: { updateNodeSize: 'size', updateNodeData: 'data' },
}));

describe('DAG loading feedback', () => {
  beforeEach(() => vi.resetAllMocks());
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows a read failure and retries loading without redeploying the DAG', async () => {
    mocks.getDagGraph
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValue({ data: { nodes: [], edges: [] } });
    render(<DagGraphPage />);
    expect(await screen.findByText('DAG 状态加载失败')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试加载' }));
    await waitFor(() => expect(mocks.fromJSON).toHaveBeenCalledTimes(1));
    expect(mocks.redeployDag).not.toHaveBeenCalled();
    expect(screen.queryByText('DAG 状态加载失败')).not.toBeInTheDocument();
  });

  it('keeps the graph and warns that its state is old after a polling failure', async () => {
    mocks.getDagGraph
      .mockResolvedValueOnce({
        data: { nodes: [{ id: 1, status: T_RUNNING }], edges: [] },
      })
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({ data: { nodes: [], edges: [] } });
    vi.useFakeTimers();
    await act(async () => {
      render(<DagGraphPage />);
    });
    await act(async () => {
      vi.advanceTimersByTime(3000);
    });
    expect(
      screen.getByText('DAG 状态刷新失败，当前显示上次成功结果'),
    ).toBeInTheDocument();
    expect(mocks.fromJSON).toHaveBeenCalledTimes(1);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '重试加载' }));
    });
    expect(mocks.fromJSON).toHaveBeenCalledTimes(1);
    expect(mocks.emit).toHaveBeenCalledWith('data', {});
  });

  it('does not draw or start polling when a request completes after unmount', async () => {
    let resolve!: (value: unknown) => void;
    mocks.getDagGraph.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    vi.useFakeTimers();
    const { unmount } = render(<DagGraphPage />);
    unmount();
    await act(async () =>
      resolve({ data: { nodes: [{ id: 1, status: T_RUNNING }], edges: [] } }),
    );
    await act(async () => {
      vi.advanceTimersByTime(3000);
    });
    expect(mocks.fromJSON).not.toHaveBeenCalled();
    expect(mocks.emit).not.toHaveBeenCalled();
    expect(mocks.getDagGraph).toHaveBeenCalledTimes(1);
  });
});
