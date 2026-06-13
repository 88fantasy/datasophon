declare namespace DATASOPHON {
  /** 上传临时文件返回值（UploadTempFile 实体） */
  interface UploadedFile {
    id: number;
    fileName?: string;
    filePath?: string;
    fileSize?: number;
    createTime?: string;
  }

  /** 校验部署清单返回值（ValidateResultVO） */
  interface ValidateResult {
    errors?: string[];
    roles?: Array<{
      serviceName: string;
      version: string;
      roleName: string;
      deployHosts: string[];
    }>;
    k8sServices?: Array<{
      serviceName: string;
      version: string;
      namespace: string;
      metaFileType: string;
    }>;
  }

  /** 执行部署返回值（InstallResult），含 dagId 用于跳转 DAG 图 */
  interface DeployResult {
    dagId: string;
  }

  /**
   * 分片合并进度（MergeProgressVO）。
   * state: 0=初始 | 2=处理中 | 1=完成 | -1=异常
   * progress: 0-1 double，由后端计算 (merge+md5) / (total*2)
   */
  interface MergeProgress {
    state: number;
    error?: string;
    total: number;
    merge: number;
    md5: number;
    progress: number;
  }

  /**
   * 导入组件进度（ImportCompProgressVO）。
   * state: 0=初始化 | 2=解析元数据 | 3=解压安装包 | 4=保存数据 |
   *        5=上传安装包到nexus | 6=上传镜像到nexus | 7=上传helm包到nexus |
   *        1=成功 | -1=失败 | -2=进度对象不存在
   */
  interface ImportCompProgress {
    progressId: number;
    state: number;
    error?: string;
    total: number;
    step: number;
    progress: number;
  }

  /** 安装组件操作请求体 */
  interface InstallComponentReq {
    meteFileId: number;
    pkgFileId?: number;
    contentDecodePasswd: string;
  }
}
