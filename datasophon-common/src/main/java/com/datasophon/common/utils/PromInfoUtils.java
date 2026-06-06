/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.common.utils;

import com.datasophon.common.Constants;
import com.datasophon.common.model.PromMetricInfo;
import com.datasophon.common.model.PromResponceInfo;
import com.datasophon.common.model.PromResultInfo;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;

import cn.hutool.http.HttpUtil;

/**
 * @Description:
 */
public class PromInfoUtils {
    
    private static final Logger log = LoggerFactory.getLogger(PromInfoUtils.class);
    
    public static List<PromResultInfo> getPrometheusMetrics(String promURL, String promQL) {
        
        // log.info("请求地址：{}，请求QL：{}", promURL, promQL);
        Map param = new HashMap<String, String>();
        param.put(Constants.QUERY, promQL);
        String http = null;
        try {
            http = HttpUtil.get(promURL, param, 10000);
        } catch (Exception e) {
            log.error("请求地址：{}，请求QL：{}，异常信息：{}", promURL, promQL, e);
        }
        PromResponceInfo responceInfo = JSON.parseObject(http, PromResponceInfo.class);
        // log.info("请求地址：{}，请求QL：{}，返回信息：{}", promURL, promQL, responceInfo);
        if (Objects.isNull(responceInfo)) {
            return null;
        }
        String status = responceInfo.getStatus();
        if (StringUtils.isBlank(status)
                || !Constants.SUCCESS.equals(status)) {
            return null;
        }
        List<PromResultInfo> result = responceInfo.getData().getResult();
        return result;
    }
    
    public static String getSinglePrometheusMetric(String promURL, String promQL) {
        
        // log.info("请求地址：{}，请求QL：{}", promURL, promQL);
        Map param = new HashMap<String, String>();
        param.put(Constants.QUERY, promQL);
        String http = null;
        try {
            http = HttpUtil.get(promURL, param, 10000);
        } catch (Exception e) {
            log.error("请求地址：{}，请求QL：{}，异常信息：{}", promURL, promQL, e.getMessage());
        }
        PromResponceInfo responceInfo = JSON.parseObject(http, PromResponceInfo.class);
        // log.info("请求地址：{}，请求QL：{}，返回信息：{}", promURL, promQL, responceInfo);
        if (Objects.isNull(responceInfo)) {
            return null;
        }
        String status = responceInfo.getStatus();
        if (StringUtils.isBlank(status)
                || !Constants.SUCCESS.equals(status)) {
            log.error("请求地址：{}，请求QL：{}，异常信息：{}", promURL, promQL, "status is failed");
            return null;
        }
        List<PromResultInfo> result = responceInfo.getData().getResult();
        if (result.size() > 0) {
            return result.get(0).getValue()[1];
        }
        return null;
    }
    
    public static void main(String[] args) {
        // List<PromResultInfo> windowsOsInfo = getWindowsInfo("http://10.0.50.225:9090/api/v1/query",
        // PromConstants.WINDOWS_OS_INFO);
        
        List<PromResultInfo> hadoop_nameNode_threads = getPrometheusMetrics("http://172.31.96.16:9090/api/v1/query",
                "up{job=\"hdfs\",instance=\"172.31.96.16:27001\"}");
        for (PromResultInfo hadoop_nameNode_thread : hadoop_nameNode_threads) {
            PromMetricInfo metric = hadoop_nameNode_thread.getMetric();
            log.info(metric.getName() + ":" + hadoop_nameNode_thread.getValue()[1]);
        }
    }
    
}
