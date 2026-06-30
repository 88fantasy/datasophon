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
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));

vi.mock('@ant-design/pro-components', async () => {
  const React = await import('react');
  const field = ({ label }: { label?: ReactNode }) => <div>{label}</div>;
  return {
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
      }, [request]);
      return <div>{JSON.stringify(data)}</div>;
    },
  };
});

vi.mock('./service', () => ({
  getCollectorConfig: vi.fn(),
  getCollectorMonitor: vi.fn(),
  pushCollectorConfig: vi.fn(),
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
        refusedTotal: 1,
        processorDroppedTotal: 1,
      },
    },
  ],
};

describe('ObservabilityCollector', () => {
  beforeEach(() => {
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

    expect(await screen.findByText(/worker-1/)).toBeInTheDocument();
    expect(screen.getByText(/"queueSize":10/)).toBeInTheDocument();
    expect(screen.getByText(/"sendFailedTotal":2/)).toBeInTheDocument();
  });
});
