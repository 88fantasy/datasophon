import type { Request, Response } from 'express';

const projects = [
  { code: 900001, name: '合成批处理项目', description: 'Wave 1 synthetic project', owner: 'readonly' },
  { code: 900002, name: '合成流处理项目', description: 'Wave 1 synthetic project', owner: 'readonly' },
];

export default {
  'GET /ddh/api/v2/cluster/list': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: [
        {
          id: 7,
          clusterName: 'Wave 1 合成集群',
          clusterCode: 'wave1-synthetic',
          clusterFrame: 'DDP-2.0.0',
          archType: 'physical',
        },
      ],
    });
  },
  'GET /ddh/api/v2/cluster/7/service/instance/list': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: [
        {
          id: 33,
          serviceName: 'DS',
          label: 'DolphinScheduler',
          catalog: 'APPLICATION',
          serviceStateCode: 2,
          alertNum: 0,
          needRestart: false,
        },
        {
          id: 34,
          serviceName: 'HDFS',
          label: 'HDFS',
          catalog: 'APPLICATION',
          serviceStateCode: 2,
          alertNum: 0,
          needRestart: false,
        },
      ],
    });
  },
  'GET /ddh/api/v2/cluster/7/service/instance/33': (_req: Request, res: Response) => {
    res.json({ success: true, data: { id: 33, serviceName: 'DS' } });
  },
  'GET /ddh/api/v2/cluster/7/service/instance/33/role/webuis': (_req: Request, res: Response) => {
    res.json({ success: true, data: [] });
  },
  'GET /ddh/api/v2/cluster/7/service/instance/34': (_req: Request, res: Response) => {
    res.json({ success: true, data: { id: 34, serviceName: 'HDFS' } });
  },
  'GET /ddh/api/v2/cluster/7/service/instance/34/role/webuis': (_req: Request, res: Response) => {
    res.json({ success: true, data: [] });
  },
  'GET /ddh/api/v2/ds/projects': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: { list: projects, total: projects.length, pageNo: 1, pageSize: 200 },
    });
  },
  'GET /ddh/api/v2/ds/workflows': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: {
        list: [
          {
            code: 800001,
            name: '合成工作流',
            version: 1,
            releaseState: 'ONLINE',
            owner: 'readonly',
            updateTime: '2026-08-25T10:20:30',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      },
    });
  },
  'GET /ddh/api/v2/ds/workflows/800001/instances': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: {
        list: [
          {
            id: 810001,
            workflowCode: 800001,
            name: '合成工作流-20260825102001',
            state: 'SUCCESS',
            startTime: '2026-08-25T10:20:01',
            endTime: '2026-08-25T10:20:17',
            durationSeconds: 16,
            host: 'synthetic-worker:1234',
            commandType: 'START_PROCESS',
            dryRun: false,
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 10,
      },
    });
  },
  'GET /ddh/api/v2/ds/instances/810001/dag': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: {
        instance: { id: 810001, name: '合成工作流-20260825102001', state: 'SUCCESS' },
        nodes: [
          {
            taskCode: 820001,
            name: 'synthetic-shell',
            taskType: 'SHELL',
            taskExecuteType: 'BATCH',
            flowType: 'BATCH',
            taskInstanceId: 830001,
            state: 'SUCCESS',
          },
        ],
        edges: [],
        locations: [],
      },
    });
  },
};
