import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DorisDashboardToolbar from './DorisDashboardToolbar';

vi.mock('@umijs/max', () => ({
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));

vi.mock('../../_shared/DashboardToolbar', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

describe('DorisDashboardToolbar', () => {
  it('hides the cluster selector in embedded mode', () => {
    render(
      <DorisDashboardToolbar
        hideClusterSelect
        cluster="DorisFE"
        clusters={['DorisFE', 'DorisBE']}
        onClusterChange={vi.fn()}
        feInstances={[]}
        selectedFeInstances={[]}
        onFeInstancesChange={vi.fn()}
        beInstances={[]}
        selectedBeInstances={[]}
        onBeInstancesChange={vi.fn()}
        rateInterval="2m"
        onRateIntervalChange={vi.fn()}
        timeRange="1h"
        onTimeRangeChange={vi.fn()}
        refreshInterval="30s"
        onRefreshIntervalChange={vi.fn()}
        onRefresh={vi.fn()}
      />,
    );

    expect(
      screen.queryByLabelText('pages.dorisMonitor.toolbar.cluster'),
    ).not.toBeInTheDocument();
  });
});
