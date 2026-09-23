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

import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SeaTunnelDashboard from './index';

const locale = vi.hoisted(() => ({ language: 'zh-CN' }));

vi.mock('@umijs/max', async () => {
  const zh = (await import('@/locales/zh-CN/seaTunnel')).default;
  const en = (await import('@/locales/en-US/seaTunnel')).default;
  const intl = {
    formatMessage: ({ id }: { id: string }, values?: Record<string, string>) =>
      ((locale.language === 'en-US' ? en : zh) as Record<string, string>)[
        id
      ]?.replace(/\{(\w+)\}/g, (_, key) => values?.[key] ?? '') ?? id,
  };
  return { useIntl: () => intl };
});

const mocks = vi.hoisted(() => ({
  useSeaTunnelDashboard: vi.fn(),
  timeSeriesPanels: [] as Array<Record<string, unknown>>,
}));

vi.mock('./hooks/useSeaTunnelDashboard', () => ({
  useSeaTunnelDashboard: mocks.useSeaTunnelDashboard,
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
vi.mock('../_shared/panels/TimeSeriesPanel', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('../_shared/panels/TimeSeriesPanel')>();
  return {
    ...actual,
    default: (props: Record<string, unknown>) => {
      mocks.timeSeriesPanels.push(props);
      return null;
    },
  };
});

describe('SeaTunnelDashboard resource metrics', () => {
  beforeEach(() => {
    locale.language = 'zh-CN';
    mocks.timeSeriesPanels.length = 0;
    mocks.useSeaTunnelDashboard.mockReturnValue({
      instant: {},
      series: {
        'ST-C10': [
          { time: 1, value: 1024, series: 'RSS (master-1)' },
          { time: 1, value: 1024, series: 'open fds (master-1)' },
          { time: 1, value: 2048, series: 'max fds (master-1)' },
        ],
      },
      instances: [],
      loading: false,
      failedPanelIds: [],
    });
  });

  it('formats RSS as bytes and file descriptors as counts', () => {
    render(<SeaTunnelDashboard clusterId={1} />);

    const panels = mocks.timeSeriesPanels as Array<{
      title: string;
      data: Array<{ series: string }>;
      yFormatter?: (value: number) => string;
    }>;
    const rssPanel = panels.find(({ title }) => title === 'RSS');
    const fileDescriptorPanel = panels.find(({ title }) => title === 'FD 数量');

    expect(rssPanel?.data.map(({ series }) => series)).toEqual([
      'RSS (master-1)',
    ]);
    expect(rssPanel?.yFormatter?.(1024)).toBe('1 KB');
    expect(fileDescriptorPanel?.data.map(({ series }) => series)).toEqual([
      'open fds (master-1)',
      'max fds (master-1)',
    ]);
    expect(fileDescriptorPanel?.yFormatter?.(1024)).toBe('1,024');
  });

  it('renders English panel titles in en-US', () => {
    locale.language = 'en-US';
    render(<SeaTunnelDashboard clusterId={1} />);

    expect(mocks.timeSeriesPanels).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ title: 'File Descriptors' }),
      ]),
    );
  });
});
