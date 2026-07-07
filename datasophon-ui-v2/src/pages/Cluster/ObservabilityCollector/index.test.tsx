import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ConfigTab from './ConfigTab';
import MonitorTab from './MonitorTab';
import {
  getCollectorConfig,
  getCollectorMonitor,
  pushCollectorConfig,
} from './service';

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
  const field = ({ label }: { label?: ReactNode }) => <div>{label}</div>;
  return {
    GridContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
    ProForm: ({
      children,
      initialValues,
      onFinish,
      submitter,
    }: {
      children: ReactNode;
      initialValues: Record<string, unknown>;
      onFinish: (values: Record<string, unknown>) => Promise<boolean>;
      submitter: { searchConfig: { submitText: string } };
    }) => (
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void onFinish(initialValues);
        }}
      >
        {children}
        <button type="submit">{submitter.searchConfig.submitText}</button>
      </form>
    ),
    ProFormDigit: field,
    ProFormSelect: field,
    ProFormText: field,
    ProFormTextArea: field,
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
  getCollectorConfig: vi.fn(),
  getCollectorMonitor: vi.fn(),
  pushCollectorConfig: vi.fn(),
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
    vi.mocked(getCollectorConfig).mockResolvedValue({
      code: 200,
      data: [
        {
          name: 'batchSize',
          label: 'Batch size',
          type: 'input-number',
          value: '8192',
          required: true,
          configurableInWizard: true,
          hidden: false,
        },
        {
          name: 'rawYaml',
          label: 'Raw YAML',
          type: 'textarea',
          value: '',
          hidden: true,
        },
      ],
    });
    vi.mocked(getCollectorMonitor).mockResolvedValue(monitorResult);
    vi.mocked(pushCollectorConfig).mockResolvedValue({
      code: 200,
      data: undefined,
    });
  });

  it('renders DDL fields and submits complete node configuration', async () => {
    render(<ConfigTab clusterId={7} />);

    await screen.findByText('Batch size');
    fireEvent.click(screen.getByRole('button', { name: 'Push and restart' }));

    await waitFor(() =>
      expect(pushCollectorConfig).toHaveBeenCalledWith(
        7,
        'worker-1',
        expect.objectContaining({ batchSize: '8192', rawYaml: '' }),
      ),
    );
  });

  it('renders node health and metrics through ProTable request', async () => {
    render(<MonitorTab clusterId={7} />);

    expect(await screen.findByText('Collector Health')).toBeInTheDocument();
    expect(screen.getAllByText('Queue usage').length).toBeGreaterThan(0);
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
