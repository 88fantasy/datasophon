/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GravitinoDashboard from './index';

const mocks = vi.hoisted(() => ({
  useGravitinoDashboard: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }, values?: Record<string, string>) =>
      values?.panels ? `${id}: ${values.panels}` : id,
  }),
}));

vi.mock('./hooks/useGravitinoDashboard', () => ({
  useGravitinoDashboard: mocks.useGravitinoDashboard,
}));

vi.mock('../_shared/MonitorDashboardLayout', () => ({
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock('../_shared/PanelCol', () => ({
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock('../_shared/panels/StatPanel', () => ({
  default: () => <div />,
}));
vi.mock('../_shared/panels/TimeSeriesPanel', () => ({
  default: () => <div />,
}));
vi.mock('./toolbar/GravitinoDashboardToolbar', () => ({
  default: () => <div />,
}));

describe('GravitinoDashboard partial failures', () => {
  beforeEach(() => {
    mocks.useGravitinoDashboard.mockReturnValue({
      instant: {
        nodeCount: 1,
        httpQps: 0,
        jettyThreadUsage: 10,
        queuedRequests: Number.NaN,
        activeConnections: 1,
        heapUsage: 20,
      },
      series: {},
      instances: [],
      loading: false,
      failedPanelIds: ['G04', 'G13'],
    });
  });

  it('surfaces the failed panel ids without hiding the rest of the dashboard', () => {
    render(<GravitinoDashboard clusterId={1} />);

    expect(
      screen.getByText('pages.gravitinoMonitor.partialLoad.title'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'pages.gravitinoMonitor.partialLoad.description: G04, G13',
      ),
    ).toBeInTheDocument();
  });
});
