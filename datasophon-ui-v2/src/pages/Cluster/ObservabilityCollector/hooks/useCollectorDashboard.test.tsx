import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { DorisPanelDescriptor } from '../../../monitor/_shared/dorisService';
import { useCollectorDashboard } from './useCollectorDashboard';

const dashboardCalls: Array<{
  panelDescriptors: Record<string, DorisPanelDescriptor>;
  panelIds: string[];
}> = [];

vi.mock('../../../monitor/_shared/useDorisDashboardData', () => ({
  useDorisDashboardData: (params: {
    panelDescriptors: Record<string, DorisPanelDescriptor>;
    panelIds: string[];
  }) => {
    dashboardCalls.push({
      panelDescriptors: params.panelDescriptors,
      panelIds: params.panelIds,
    });
    return { instant: {}, series: {}, loading: false };
  },
}));

function Harness() {
  useCollectorDashboard({ clusterId: 7, timeRange: '1h', refreshKey: 0 });
  return null;
}

describe('useCollectorDashboard', () => {
  it('passes a stable panelIds reference to avoid refetch loops', () => {
    dashboardCalls.length = 0;
    const { rerender } = render(<Harness />);

    rerender(<Harness />);

    expect(dashboardCalls).toHaveLength(2);
    expect(dashboardCalls[1].panelIds).toBe(dashboardCalls[0].panelIds);
  });
});
