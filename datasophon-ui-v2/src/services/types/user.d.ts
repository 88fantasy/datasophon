declare namespace DATASOPHON {
  /** GET /v2/user/list 响应体（只含 id + username，不含 password） */
  interface UserResponse {
    id: number;
    username: string;
  }

  /** @deprecated 旧 DAO 镜像，迁移完成后删除，暂保留供未迁移文件使用 */
  interface UserInfo {
    id: number;
    username: string;
    email?: string;
    phone?: string;
    password?: string;
    createTime?: string;
    userType?: number;
  }

  interface CurrentUser extends UserInfo {
    /** 'admin' | 'user' — used by access.ts for permission gating */
    access?: string;
    /** display name shown in ProLayout avatar */
    name?: string;
    /** avatar URL shown in ProLayout header */
    avatar?: string;
  }

  /** 用户管理页展示字段，明确不含 password */
  interface UserInfoResponse {
    id: number;
    username: string;
    email?: string;
    phone?: string;
    createTime?: string;
    userType?: number;
  }

  /** 用户分页响应体 */
  interface UserPageResponse {
    records: UserInfoResponse[];
    total: number;
  }

  /** 新建用户请求体 */
  interface CreateUserRequest {
    username: string;
    password: string;
    email?: string;
    phone?: string;
    userType?: number;
  }

  /** 编辑用户请求体（不含 password） */
  interface UpdateUserRequest {
    username: string;
    email?: string;
    phone?: string;
    userType?: number;
  }
}
