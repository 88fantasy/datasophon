import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import MonitorTab from './MonitorTab';
import { getCollectorMonitor } from './service';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({
      id,
      defaultMessage,
    }: {
      id: string;
      defaultMessage?: string;
    }) => defaultMessage ?? id,
  }),
}));

vi.mock('@ant-design/pro-components', async () => {
  const React = await import('react');
  return {
    GridContent: ({ children }: { children: React.ReactNode }) => (
      <div>{children}</div>
    ),
    ProTable: ({
      request,
    }: {
      request: () => Promise<{ data: unknown[] }>;
    }) => {
      const [data, setData] = React.useState<unknown[]>([]);
      React.useEffect(() => {
        void request().then((result) => setData(result.data));
      }, []);
      return <div>{JSON.stringify(data)}</div>;
    },
  };
});

vi.mock('./service', () => ({
  getCollectorMonitor: vi.fn(),
}));

vi.mock('./hooks/useCollectorDashboard', () => ({
  useCollectorDashboard: () => ({
    series: {
      queueUsage: [],
      sentRate: [],
      failedRate: [],
      refusedDroppedRate: [],
      uptime: [],
    },
    loading: false,
  }),
}));

const monitorResult = {
  code: 200,
  data: [
    {
      hostname: 'worker-1',
      healthy: true,
      metrics: {
        queueSize: 10,
        queueCapacity: 100,
        sentTotal: 200,
        sendFailedTotal: 2,
        receiverFailedTotal: 0,
        refusedTotal: 1,
        processorDroppedTotal: 1,
        processUptime: 3600,
      },
    },
  ],
};

describe('ObservabilityCollector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getCollectorMonitor).mockResolvedValue(monitorResult);
  });

  it('renders node health and metrics through ProTable request', async () => {
    render(<MonitorTab clusterId={7} />);

    expect(await screen.findAllByText('Queue usage')).not.toHaveLength(0);
    expect(screen.getByText('Node details')).toBeInTheDocument();
    expect(await screen.findByText(/worker-1/)).toBeInTheDocument();
    expect(screen.getByText(/"queueSize":10/)).toBeInTheDocument();
    expect(screen.getByText(/"sendFailedTotal":2/)).toBeInTheDocument();
    expect(screen.getByText(/"processUptime":3600/)).toBeInTheDocument();
  });

  it('waits for a valid cluster id before loading monitor data', async () => {
    const { rerender } = render(<MonitorTab clusterId={0} />);

    await waitFor(() => expect(getCollectorMonitor).not.toHaveBeenCalled());

    rerender(<MonitorTab clusterId={7} />);

    await waitFor(() => expect(getCollectorMonitor).toHaveBeenCalledWith(7));
    expect(getCollectorMonitor).not.toHaveBeenCalledWith(0);
  });
});
