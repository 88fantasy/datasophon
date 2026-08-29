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
    res.json({
      success: true,
      data: [{ name: 'DolphinScheduler', webUrl: 'http://ds.example.test' }],
    });
  },
  'GET /ddh/api/v2/cluster/7/service/instance/34': (_req: Request, res: Response) => {
    res.json({ success: true, data: { id: 34, serviceName: 'HDFS' } });
  },
  'GET /ddh/api/v2/cluster/7/service/instance/34/role/webuis': (_req: Request, res: Response) => {
    res.json({ success: true, data: [] });
  },
  'GET /ddh/api/v2/ds/projects': (req: Request, res: Response) => {
    const failure = req.headers.cookie
      ?.split(';')
      .map((item) => item.trim())
      .find((item) => item.startsWith('dsMockFailure='))
      ?.split('=')[1];
    if (failure === 'token-missing') {
      res.json({
        success: false,
        errorCode: 400,
        errorMessage: '请在 DS 服务配置中填写 apiToken',
      });
      return;
    }
    if (failure === 'token-invalid') {
      res.status(401).json({
        success: false,
        errorCode: 401,
        errorMessage: 'DS apiToken 已失效',
      });
      return;
    }
    if (failure === 'unavailable') {
      res.status(502).json({
        success: false,
        errorCode: 502,
        errorMessage: 'DS Open API 不可达或请求超时',
      });
      return;
    }
    res.json({
      success: true,
      data: { list: projects, total: projects.length, pageNo: 1, pageSize: 200 },
    });
  },
  'GET /ddh/api/v2/ds/workflows': (req: Request, res: Response) => {
    const streaming = String(req.query.projectCode) === '900002';
    res.json({
      success: true,
      data: {
        list: [
          {
            code: streaming ? 800002 : 800001,
            name: streaming ? '合成流工作流' : '合成批工作流',
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
        instance: { id: 810001, name: '合成批工作流-20260825102001', state: 'SUCCESS' },
        nodes: [
          {
            taskCode: 820001,
            name: 'synthetic-spark-batch',
            taskType: 'SPARK',
            taskExecuteType: 'BATCH',
            flowType: 'BATCH',
            taskInstanceId: 830001,
            state: 'SUCCESS',
            metrics: {
              kind: 'BATCH',
              runCount: 7,
              outputs: [
                { name: 'synthetic/source_700', rowCount: 700, size: 7096 },
                { name: 'synthetic/target_234', rowCount: 234, size: 3450 },
                { name: 'synthetic/audit_1', rowCount: 1, size: 128 },
              ],
            },
          },
          {
            taskCode: 820002,
            name: 'synthetic-unbound-shell',
            taskType: 'SHELL',
            taskExecuteType: 'BATCH',
            flowType: 'BATCH',
            taskInstanceId: 830002,
            state: 'SUCCESS',
            metricsError: 'NOT_BOUND',
          },
        ],
        edges: [{ from: 820001, to: 820002 }],
        locations: [],
      },
    });
  },
  'GET /ddh/api/v2/ds/workflows/800002/instances': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: {
        list: [
          {
            id: 810002,
            workflowCode: 800002,
            name: '合成流工作流-20260825110001',
            state: 'RUNNING_EXECUTION',
            startTime: '2026-08-25T11:00:01',
            durationSeconds: 3600,
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
  'GET /ddh/api/v2/ds/instances/810002/dag': (_req: Request, res: Response) => {
    res.json({
      success: true,
      data: {
        instance: {
          id: 810002,
          name: '合成流工作流-20260825110001',
          state: 'RUNNING_EXECUTION',
        },
        nodes: [
          {
            taskCode: 820003,
            name: 'synthetic-flink-stream',
            taskType: 'FLINK_STREAM',
            taskExecuteType: 'STREAM',
            flowType: 'STREAM',
            taskInstanceId: 830003,
            state: 'RUNNING_EXECUTION',
            metrics: {
              kind: 'STREAM',
              jobId: '0123456789abcdef0123456789abcdef',
              jobName: 'ds-7-830003-synthetic-stream',
              rowsPerSecond: 22.8,
              approximate: true,
              processedApprox: 1234567,
              since: '2026-08-25T11:00:36Z',
            },
          },
        ],
        edges: [],
        locations: [],
      },
    });
  },
};
