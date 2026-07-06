type ApiResponseLike = {
  success?: boolean;
  errorMessage?: string;
  msg?: string;
  message?: string;
  code?: number | string;
};

function readMessage(response: ApiResponseLike, fallback: string) {
  return response.errorMessage || response.msg || response.message || fallback;
}

export function getApiFailureMessage(
  response: unknown,
  fallback = '操作失败',
): string | undefined {
  if (!response || typeof response !== 'object') {
    return fallback;
  }

  const body = response as ApiResponseLike;
  if (body.success === false) {
    return readMessage(body, fallback);
  }

  if (body.code !== undefined && Number(body.code) !== 200) {
    return readMessage(body, fallback);
  }

  return undefined;
}
