import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import ClusterStatCard from './ClusterStatCard';

describe('ClusterStatCard', () => {
  it('renders a positive alert delta as a risk and a negative delta as an improvement', () => {
    const { rerender } = render(
      <ClusterStatCard
        title="告警数量"
        value={8}
        color="#faad14"
        icon={<span />}
        delta={3}
        deltaLabel="日环比"
        positiveIsGood={false}
      />,
    );

    expect(screen.getByText('+3')).toHaveStyle({ color: '#ff4d4f' });

    rerender(
      <ClusterStatCard
        title="告警数量"
        value={5}
        color="#faad14"
        icon={<span />}
        delta={-3}
        deltaLabel="日环比"
        positiveIsGood={false}
      />,
    );

    expect(screen.getByText('-3')).toHaveStyle({ color: '#52c41a' });
  });
});
