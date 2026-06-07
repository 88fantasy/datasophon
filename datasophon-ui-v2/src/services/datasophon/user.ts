import { request } from '@umijs/max';

// ─── 类型定义 ───────────────────────────────────────────────

export interface UserPageData {
  records: DATASOPHON.UserInfo[];
  total: number;
}

// ─── API ─────────────────────────────────────────────────────

/** 分页用户列表 */
export function listUsers(params: {
  page: number;
  pageSize: number;
  username?: string;
}) {
  return request<{ data: UserPageData }>('/user/page', {
    method: 'GET',
    params,
  });
}

/** 新建用户 */
export function createUser(body: Partial<DATASOPHON.UserInfo>) {
  return request<{ data: void }>('/user', {
    method: 'POST',
    data: body,
  });
}

/** 编辑用户（不含密码） */
export function updateUser(id: number, body: Partial<DATASOPHON.UserInfo>) {
  return request<{ data: void }>(`/user/${id}`, {
    method: 'PUT',
    data: body,
  });
}

/** 重置密码 */
export function resetPassword(id: number, password: string) {
  return request<{ data: void }>(`/user/${id}/password`, {
    method: 'POST',
    data: { password },
  });
}

/** 批量删除用户 */
export function deleteUsers(ids: number[]) {
  return request<{ data: void }>('/user', {
    method: 'DELETE',
    data: ids,
  });
}
