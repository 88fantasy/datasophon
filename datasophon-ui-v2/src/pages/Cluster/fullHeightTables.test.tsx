import { render, screen } from '@testing-library/react';
import type { CSSProperties, ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClusterContext from '@/context/ClusterContext';
import CommandList from './Command';
import HostManage from './HostManage';

interface MockProTableProps {
  headerTitle?: ReactNode;
  style?: CSSProperties;
  cardProps?: { style?: CSSProperties };
}

const proTableSpy = vi.hoisted(() => vi.fn());

vi.mock('@ant-design/pro-components', () => ({
  ProTable: (props: MockProTableProps) => {
    proTableSpy(props);
    return <div>{props.headerTitle}</div>;
  },
}));
vi.mock('@/services/dag', () => ({ listDagCommands: vi.fn() }));
vi.mock('@/services/host', () => ({
  deleteClusterHosts: vi.fn(),
  listClusterHosts: vi.fn(),
}));
vi.mock('./HostManage/components/AssignLabelModal', () => ({
  default: () => null,
}));
vi.mock('./HostManage/components/AssignRackModal', () => ({
  default: () => null,
}));
vi.mock('./HostManage/components/LabelManageModal', () => ({
  default: () => null,
}));
vi.mock('./HostManage/components/RoleListModal', () => ({
  default: () => null,
}));

const expectLatestTableToFillPage = () => {
  const props = proTableSpy.mock.lastCall?.[0] as MockProTableProps | undefined;
  expect(props?.style).toEqual({ minHeight: '100%' });
  expect(props?.cardProps?.style).toEqual({ minHeight: '100%' });
};

describe('cluster full-height tables', () => {
  beforeEach(() => {
    proTableSpy.mockClear();
  });

  it('stretches the host table card to the page bottom', () => {
    render(
      <ClusterContext.Provider value={{ clusterId: 7 } as never}>
        <HostManage />
      </ClusterContext.Provider>,
    );

    expect(screen.getByText('主机列表')).toBeInTheDocument();
    expectLatestTableToFillPage();
  });

  it('stretches the command table card to the page bottom', () => {
    render(
      <ClusterContext.Provider value={{ clusterId: 7 } as never}>
        <CommandList />
      </ClusterContext.Provider>,
    );

    expect(screen.getByText('命令历史')).toBeInTheDocument();
    expectLatestTableToFillPage();
  });
});
