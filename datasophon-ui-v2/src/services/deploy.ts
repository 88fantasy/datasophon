import { request } from '@umijs/max';

/** 上传部署文件（multipart），返回临时文件信息（含 id 作为 deployFileId）。 */
export function uploadDeployFile(clusterId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request<{ data: DATASOPHON.UploadedFile }>(
    `/cluster/${clusterId}/deploy/upload`,
    {
      method: 'POST',
      data: formData,
      requestType: 'form',
    },
  );
}

/** 校验部署清单（可用于预检，也用于提交前服务端二次校验）。 */
export function validateDeploymentFile(
  clusterId: number,
  req: { deployFileId: number; contentDecodePasswd: string },
) {
  return request<{ data: DATASOPHON.ValidateResult }>(
    `/cluster/${clusterId}/deploy/validate-deployment-file`,
    {
      method: 'POST',
      data: req,
    },
  );
}

/** 执行部署，返回 dagId，前端跳转 DAG 全屏图。 */
export function deployManifest(
  clusterId: number,
  req: { deployFileId: number; contentDecodePasswd: string },
) {
  return request<{ data: DATASOPHON.DeployResult }>(
    `/cluster/${clusterId}/deploy/deploy`,
    {
      method: 'POST',
      data: req,
    },
  );
}

// ─── 切片 7b：部署包 ──────────────────────────────────────────────────────

/** 校验配置元数据文件（meta yaml）。 */
export function validMetaFile(clusterId: number, req: DATASOPHON.InstallComponentReq) {
  return request<{ data: DATASOPHON.ValidateResult }>(
    `/cluster/${clusterId}/deploy/valid-meta-file`,
    { method: 'POST', data: req },
  );
}

/** 触发导入安装组件（异步），返回含 progressId。 */
export function importComponent(clusterId: number, req: DATASOPHON.InstallComponentReq) {
  return request<{ data: DATASOPHON.ImportCompProgress }>(
    `/cluster/${clusterId}/deploy/import-cmp`,
    { method: 'POST', data: req },
  );
}

/** 查询导入进度。 */
export function queryImportProgress(clusterId: number, progressId: number) {
  return request<{ data: DATASOPHON.ImportCompProgress }>(
    `/cluster/${clusterId}/deploy/query-progress`,
    { method: 'POST', data: { progressId } },
  );
}

/** 查询分片合并进度。 */
export function queryMergeProgress(clusterId: number, progressId: number) {
  return request<{ data: DATASOPHON.MergeProgress }>(
    `/cluster/${clusterId}/deploy/query-merge-progress`,
    { method: 'POST', data: { progressId } },
  );
}
