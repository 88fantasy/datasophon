import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import RecentAlertsPanel from './RecentAlertsPanel';
import ServiceHealthPanel from './ServiceHealthPanel';

describe('dashboard table overflow', () => {
  it('keeps a long service name available and its detail action usable', () => {
    const name = 'Doris 多副本存算一体服务';
    const onOpenService = vi.fn();
    const { container } = render(
      <ServiceHealthPanel
        title="服务健康"
        columnLabels={{
          service: '服务',
          roles: '角色',
          health: '健康度',
          alertNum: '告警',
          state: '状态',
        }}
        emptyText="暂无服务"
        viewMoreLabel="更多"
        data={[
          {
            serviceName: 'DORIS',
            label: name,
            healthPercent: null,
            runningRoles: 1,
            totalRoles: 1,
            abnormalRoles: 0,
            alertNum: 0,
            serviceState: '正常',
          },
        ]}
        onViewMore={vi.fn()}
        onOpenService={onOpenService}
      />,
    );
    const link = screen.getByText(name);
    expect(link).toHaveAttribute('title', name);
    fireEvent.click(link);
    expect(onOpenService).toHaveBeenCalledWith('DORIS');
    expect(container.querySelector('.ant-table-body table')).toHaveStyle({
      width: '640px',
    });
  });

  it('keeps full alert and host names available within a horizontally scrolling table', () => {
    const target = 'Doris Backend 长时间磁盘使用率超阈值';
    const hostname = 'doris-backend-production-01';
    const onOpenService = vi.fn();
    const { container } = render(
      <RecentAlertsPanel
        title="最新告警"
        levelLabels={{ warning: '警告' }}
        columnLabels={{
          level: '级别',
          target: '目标',
          hostname: '主机',
          createTime: '时间',
        }}
        emptyText="暂无告警"
        viewAllLabel="全部"
        data={[
          {
            id: 1,
            alertTargetName: target,
            hostname,
            alertLevel: 'warning',
            serviceInstanceId: 12,
          } as DATASOPHON.ClusterAlertHistoryRecord,
        ]}
        onViewAll={vi.fn()}
        onOpenService={onOpenService}
      />,
    );
    const link = screen.getByText(target);
    expect(link).toHaveAttribute('title', target);
    expect(screen.getByText(hostname)).toHaveAttribute('title', hostname);
    fireEvent.click(link);
    expect(onOpenService).toHaveBeenCalledWith(12);
    expect(container.querySelector('.ant-table-body table')).toHaveStyle({
      width: '640px',
    });
  });
});
