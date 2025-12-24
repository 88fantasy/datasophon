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

import axios from "axios";
import "./interceptors";
import { message } from "antd";

// post数据处理
export const handleParams = function (data) {
  const params = new FormData();
  for (let key in data) {
    params.append(key, data[key]);
  }
  return params;
};

export const axiosGet = function (url, params = {}) {
  return new Promise((resolve, reject) => {
    axios({
      method: "get",
      url: url,
      params: params,
    }).then((res) => {
      resolve(res.data);
    });
  });
};

export const axiosPost = function (url, params = {}) {
  return new Promise((resolve, reject) => {
    axios({
      method: "post",
      url: url,
      data: handleParams(params),
      ContentType: "application/json;charset=UTF-8",
    }).then((res) => {
      resolve(res.data);
    });
  });
};
export const axiosJsonPost = function (url, params = {}) {
  return new Promise((resolve, reject) => {
    axios({
      method: "post",
      url: url,
      data: params,
    }).then((res) => {
      resolve(res.data);
    });
  });
};

// 文件上传，params为form-data
export const axiosPostUpload = function (url, params = {}) {
  return new Promise((resolve, reject) => {
    axios({
      method: "post",
      url: url,
      data: params,
      timeout: 1000 * 60 * 60 * 24,
    }).then((res) => {
      resolve(res.data);
    });
  });
};
