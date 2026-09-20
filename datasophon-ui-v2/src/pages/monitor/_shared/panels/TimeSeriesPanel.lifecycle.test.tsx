import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { expect, it, vi } from 'vitest';
import TimeSeriesPanel from './TimeSeriesPanel';

vi.mock('@ant-design/plots', () => {
  const Chart = ({ data }: { data: Array<{ value: number }> }) => {
    const [selected, setSelected] = useState(false);
    return (
      <button type="button" onClick={() => setSelected(true)}>
        {selected ? 'selected' : 'unselected'}:{data[0].value}
      </button>
    );
  };
  return { Area: Chart, Line: Chart };
});

it('preserves chart interaction state when the time window refreshes', () => {
  const { rerender } = render(
    <TimeSeriesPanel
      title="CPU"
      data={[{ time: 1000, value: 1, series: 'node' }]}
    />,
  );
  fireEvent.click(screen.getByRole('button', { name: 'unselected:1' }));
  rerender(
    <TimeSeriesPanel
      title="CPU"
      data={[{ time: 2000, value: 2, series: 'node' }]}
    />,
  );
  expect(
    screen.getByRole('button', { name: 'selected:2' }),
  ).toBeInTheDocument();
});

it('distinguishes loading, failed queries and successful empty results', () => {
  const { rerender } = render(<TimeSeriesPanel title="CPU" loading />);
  expect(screen.getByRole('status')).toHaveTextContent('正在加载指标');
  expect(screen.queryByText(/暂无指标数据/)).not.toBeInTheDocument();
  rerender(<TimeSeriesPanel title="CPU" error />);
  expect(screen.getByRole('status')).toHaveTextContent('指标查询失败，请重试');
  rerender(<TimeSeriesPanel title="CPU" />);
  expect(screen.getByRole('status')).toHaveTextContent('暂无指标数据');
});

it('keeps an existing chart mounted while refreshing', () => {
  const data = [{ time: 1000, value: 1, series: 'node' }];
  const { rerender } = render(<TimeSeriesPanel title="CPU" data={data} />);
  fireEvent.click(screen.getByRole('button', { name: 'unselected:1' }));
  rerender(<TimeSeriesPanel title="CPU" data={data} loading />);
  expect(
    screen.getByRole('button', { name: 'selected:1' }),
  ).toBeInTheDocument();
  expect(screen.queryByText('正在加载指标…')).not.toBeInTheDocument();
});
