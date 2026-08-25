export type DsErrorKind = 'tokenMissing' | 'tokenInvalid' | 'unavailable';

interface RequestErrorShape {
  info?: { errorCode?: number; errorMessage?: string };
  data?: { errorCode?: number; errorMessage?: string };
  response?: {
    status?: number;
    data?: { errorCode?: number; errorMessage?: string };
  };
  message?: string;
}

export function classifyDsError(error: unknown): DsErrorKind {
  const value = (error ?? {}) as RequestErrorShape;
  const status =
    value.info?.errorCode ??
    value.response?.data?.errorCode ??
    value.data?.errorCode ??
    value.response?.status;
  const message =
    value.info?.errorMessage ??
    value.response?.data?.errorMessage ??
    value.data?.errorMessage ??
    value.message ??
    '';
  if (status === 401 || message.includes('apiToken 已失效')) {
    return 'tokenInvalid';
  }
  if (message.includes('填写 apiToken') || message.includes('apiToken 未配置')) {
    return 'tokenMissing';
  }
  return 'unavailable';
}
