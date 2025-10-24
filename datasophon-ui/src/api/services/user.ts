import { VUE_APP_PERMISSIONS_KEY, VUE_APP_ROLES_KEY, VUE_APP_ROUTES_KEY } from "../../config";
import { METHOD, removeAuthorization, request } from "../../utils/request";
import { LOGIN, ROUTES } from "./api";

/**
 * 登录服务
 * @param name 账户名
 * @param password 账户密码
 * @returns {Promise<AxiosResponse<T>>}
 */
export async function login(name, password) {
  return request(LOGIN, METHOD.POST, {
    name: name,
    password: password,
  });
}

export async function getRoutesConfig() {
  return request(ROUTES, METHOD.GET);
}

/**
 * 退出登录
 */
export function logout() {
  localStorage.removeItem(VUE_APP_ROUTES_KEY);
  localStorage.removeItem(VUE_APP_PERMISSIONS_KEY);
  localStorage.removeItem(VUE_APP_ROLES_KEY);
  removeAuthorization();
}
export default {
  login,
  logout,
  getRoutesConfig,
};
