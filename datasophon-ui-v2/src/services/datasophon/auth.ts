import { request } from '@umijs/max';

/** 获取当前登录用户 GET /currentUser */
export async function currentUser(options?: Record<string, unknown>) {
  return request<{ data: API.CurrentUser }>('/currentUser', {
    method: 'GET',
    ...(options || {}),
  });
}

/** 登录 POST /login/account */
export async function login(
  body: { username: string; password: string },
  options?: Record<string, unknown>,
) {
  return request<{ data: API.CurrentUser }>('/login/account', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  });
}

/** 退出登录 POST /logout */
export async function logout(options?: Record<string, unknown>) {
  return request<Record<string, unknown>>('/logout', {
    method: 'POST',
    ...(options || {}),
  });
}
