/*
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

import { message } from "antd";
import axios from "axios";
import { VUE_APP_PUBLIC_PATH } from "../config";
import { removeAuthorization } from "../utils/request";
import { invokeRelogin } from "../utils/authorityUtils";
import { account } from "../utils/account";

export const codeMessage = {
  200: "服务器成功返回请求的数据。",
  201: "新建或修改数据成功。",
  202: "一个请求已经进入后台排队（异步任务）。",
  204: "删除数据成功。",
  302: "请求被临时重定向重定向。",
  301: "请求被永久重定向重定向。",
  400: "发出的请求有错误，服务器没有进行新建或修改数据的操作。",
  401: "用户没有权限（令牌、用户名、密码错误）。",
  403: "用户得到授权，但是访问是被禁止的。",
  404: "发出的请求针对的是不存在的记录，服务器没有进行操作。",
  406: "请求的格式不可得。",
  410: "请求的资源被永久删除，且不会再得到的。",
  422: "当创建一个对象时，发生一个验证错误。",
  500: "服务器发生错误，请检查服务器。",
  502: "网关错误。",
  503: "服务不可用，服务器暂时过载或维护。",
  504: "网关超时。",
};

// axios请求拦截
axios.interceptors.request.use(
  (config) => {
    // 在请求之前操作
    if (config.method === "get") {
      // 在ie中相同请求不会重复发送
      if (window.ActiveXObject || "ActiveXObject" in window) {
        config.url = `${config.url}?${new Date().getTime()}`;
      }
    }
    config.headers["Content-Type"] = config.ContentType
      ? config.ContentType
      : "application/json;charset=UTF-8";
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

axios.interceptors.response.use(
  (response) => {
    // 对响应数据操作
    // console.log("response", response);
    return response;
  },
  (error) => {
    // console.log("response", error);

    const response = error?.response;

    //     const { response } = error;
    let errortext = codeMessage[response.status] || response.statusText;

    errortext = `【HTTPCODE:${response.status || ""}】${errortext}`;

    message.error(errortext);

    if (response?.status === 401) {
      account.clear();
      invokeRelogin();
      // return true;
    }

    const data = {
      code: response.status,
      message: errortext,
      msg: errortext,
    };

    return Promise.resolve({
      data,
      ...data,
    });
  }
);
