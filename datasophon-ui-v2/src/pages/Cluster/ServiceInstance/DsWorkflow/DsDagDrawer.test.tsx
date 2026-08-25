import { act, render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DsDagDrawer from './DsDagDrawer';
import { getDsDag } from './service';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));

vi.mock('antd', () => ({
  Alert: ({ title }: { title: ReactNode }) => <div>{title}</div>,
  Drawer: ({ open, children }: { open: boolean; children: ReactNode }) =>
    open ? <div>{children}</div> : null,
  Spin: () => <div>loading</div>,
}));

vi.mock('./DsDagGraph', () => ({ default: () => <div>graph</div> }));
vi.mock('./service', () => ({ getDsDag: vi.fn() }));

const instance: DATASOPHON.DsWorkflowInstance = {
  id: 810001,
  workflowCode: 800001,
  name: 'synthetic instance',
  state: 'RUNNING_EXECUTION',
  durationSeconds: 10,
  dryRun: false,
};

const dag: DATASOPHON.DsDag = {
  instance,
  nodes: [],
  edges: [],
  locations: [],
};

describe('DsDagDrawer polling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(getDsDag).mockResolvedValue({ success: true, data: dag });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('polls every 15 seconds only while the drawer is open', async () => {
    const { rerender } = render(
      <DsDagDrawer
        clusterId={7}
        projectCode={99}
        instance={instance}
        open
        onClose={vi.fn()}
      />,
    );
    await act(async () => Promise.resolve());
    expect(getDsDag).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(15_000);
      await Promise.resolve();
    });
    expect(getDsDag).toHaveBeenCalledTimes(2);

    rerender(
      <DsDagDrawer
        clusterId={7}
        projectCode={99}
        instance={instance}
        open={false}
        onClose={vi.fn()}
      />,
    );
    await act(async () => {
      vi.advanceTimersByTime(30_000);
      await Promise.resolve();
    });
    expect(getDsDag).toHaveBeenCalledTimes(2);
  });
});
