/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

import { VUE_APP_PERMISSIONS_KEY, VUE_APP_ROLES_KEY, VUE_APP_ROUTES_KEY, VUE_APP_USER_KEY } from "../config";

interface User {
  username: string;
  avatar: string;
  [key: string]: any;
}

interface State {
  user: User | undefined;
  permissions: string[] | null;
  roles: string[] | null;
  routesConfig: any[] | null;
}

class AccountManager {
  private static instance: AccountManager;
  private state: State;
  private readonly USER_KEY = VUE_APP_USER_KEY;
  private readonly PERMISSIONS_KEY = VUE_APP_PERMISSIONS_KEY;
  private readonly ROLES_KEY = VUE_APP_ROLES_KEY;
  private readonly ROUTES_KEY = VUE_APP_ROUTES_KEY;

  private constructor() {
    this.state = {
      user: undefined,
      permissions: null,
      roles: null,
      routesConfig: null
    };
    this.initializeState();
  }

  public static getInstance(): AccountManager {
    if (!AccountManager.instance) {
      AccountManager.instance = new AccountManager();
    }
    return AccountManager.instance;
  }

  private initializeState(): void {
    // 初始化时从 localStorage 加载所有状态
    this.loadUser();
    this.loadPermissions();
    this.loadRoles();
    this.loadRoutesConfig();
  }

  private loadFromStorage<T>(key: string): T | null {
    try {
      const data = localStorage.getItem(key);
      return data ? JSON.parse(data) : null;
    } catch (e) {
      console.error(`Error loading ${key} from storage:`, e);
      return null;
    }
  }

  private saveToStorage(key: string, value: any): void {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      console.error(`Error saving ${key} to storage:`, e);
    }
  }

  // User 相关方法
  public getUser(): User | undefined {
    if (!this.state.user) {
      this.loadUser();
    }
    return this.state.user;
  }

  private loadUser(): void {
    this.state.user = this.loadFromStorage(this.USER_KEY);
  }

  public setUser(user: User): void {
    this.state.user = user;
    this.saveToStorage(this.USER_KEY, user);
  }

  // Permissions 相关方法
  public getPermissions(): string[] {
    if (!this.state.permissions) {
      this.loadPermissions();
    }
    return this.state.permissions || [];
  }

  private loadPermissions(): void {
    this.state.permissions = this.loadFromStorage(this.PERMISSIONS_KEY) || [];
  }

  public setPermissions(permissions: string[]): void {
    this.state.permissions = permissions;
    this.saveToStorage(this.PERMISSIONS_KEY, permissions);
  }

  // Roles 相关方法
  public getRoles(): string[] {
    if (!this.state.roles) {
      this.loadRoles();
    }
    return this.state.roles || [];
  }

  private loadRoles(): void {
    this.state.roles = this.loadFromStorage(this.ROLES_KEY) || [];
  }

  public setRoles(roles: string[]): void {
    this.state.roles = roles;
    this.saveToStorage(this.ROLES_KEY, roles);
  }

  // RoutesConfig 相关方法
  public getRoutesConfig(): any[] {
    if (!this.state.routesConfig) {
      this.loadRoutesConfig();
    }
    return this.state.routesConfig || [];
  }

  private loadRoutesConfig(): void {
    this.state.routesConfig = this.loadFromStorage(this.ROUTES_KEY) || [];
  }

  public setRoutesConfig(routesConfig: any[]): void {
    this.state.routesConfig = routesConfig;
    this.saveToStorage(this.ROUTES_KEY, routesConfig);
  }

  // 清除所有数据
  public clear(): void {
    localStorage.removeItem(this.USER_KEY);
    localStorage.removeItem(this.PERMISSIONS_KEY);
    localStorage.removeItem(this.ROLES_KEY);
    localStorage.removeItem(this.ROUTES_KEY);
    this.state = {
      user: undefined,
      permissions: null,
      roles: null,
      routesConfig: null
    };
  }
}

// 导出单例实例
export const account = AccountManager.getInstance();

// 导出类型
export type { User, State };
