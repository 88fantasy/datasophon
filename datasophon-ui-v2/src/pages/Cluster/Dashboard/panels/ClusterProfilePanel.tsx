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

import {
  AppstoreOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ClusterOutlined,
  DatabaseOutlined,
  HddOutlined,
  SettingOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Descriptions } from 'antd';
import type { FC, ReactNode } from 'react';
import MonitorPanelCard from '../../../monitor/_shared/MonitorPanelCard';

function withIcon(icon: ReactNode, label: string) {
  return (
    <span>
      {icon} {label}
    </span>
  );
}

interface ClusterProfilePanelProps {
  title: string;
  labels: {
    frame: string;
    cpuArchitecture: string;
    nodeCount: string;
    totalCores: string;
    totalMem: string;
    totalDisk: string;
    clusterState: string;
    createTime: string;
  };
  profile?: DATASOPHON.ClusterDashboardProfile;
}

function formatGb(value: number) {
  if (value >= 1024) return `${(value / 1024).toFixed(1)} TB`;
  return `${value} GB`;
}

/**
 * 集群概要：替代参考图「系统信息」（OS/内核版本/CPU 型号/运行时间本项目无数据源，
 * 见 Epic 计划的偏差说明），改为展示已有的集群框架、拓扑规模与状态信息。
 */
const ClusterProfilePanel: FC<ClusterProfilePanelProps> = ({
  title,
  labels,
  profile,
}) => (
  <MonitorPanelCard title={title}>
    <Descriptions
      column={{ xs: 1, sm: 2, md: 4 }}
      size="small"
      items={[
        {
          key: 'frame',
          label: withIcon(<AppstoreOutlined />, labels.frame),
          children: profile
            ? `${profile.clusterFrame ?? '-'} ${profile.frameVersion ?? ''}`.trim()
            : '-',
        },
        {
          key: 'cpuArchitecture',
          label: withIcon(<SettingOutlined />, labels.cpuArchitecture),
          children: profile?.cpuArchitectures.length
            ? profile.cpuArchitectures.join(', ')
            : '-',
        },
        {
          key: 'nodeCount',
          label: withIcon(<ClusterOutlined />, labels.nodeCount),
          children: profile?.nodeCount ?? '-',
        },
        {
          key: 'totalCores',
          label: withIcon(<ThunderboltOutlined />, labels.totalCores),
          children: profile?.totalCores ?? '-',
        },
        {
          key: 'totalMem',
          label: withIcon(<DatabaseOutlined />, labels.totalMem),
          children: profile ? formatGb(profile.totalMemGb) : '-',
        },
        {
          key: 'totalDisk',
          label: withIcon(<HddOutlined />, labels.totalDisk),
          children: profile ? formatGb(profile.totalDiskGb) : '-',
        },
        {
          key: 'clusterState',
          label: withIcon(<CheckCircleOutlined />, labels.clusterState),
          children: profile?.clusterState ?? '-',
        },
        {
          key: 'createTime',
          label: withIcon(<ClockCircleOutlined />, labels.createTime),
          children: profile?.createTime ?? '-',
        },
      ]}
    />
  </MonitorPanelCard>
);

export default ClusterProfilePanel;
