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


package com.datasophon.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.service.ClusterServiceDashboardService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceDashboard;
import com.datasophon.dao.mapper.ClusterServiceDashboardMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

import static com.datasophon.common.Constants.GRAFANA_PATH;

@Service("clusterServiceDashboardService")
public class ClusterServiceDashboardServiceImpl
        extends
        ServiceImpl<ClusterServiceDashboardMapper, ClusterServiceDashboard>
        implements
        ClusterServiceDashboardService {

    @Value("${datasophon.proxy-grafana.enable:false}")
    private boolean proxy;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Override
    public Result getDashboardUrl(Integer clusterId) {
        ClusterServiceDashboard dashboard = getOne(new QueryWrapper<ClusterServiceDashboard>().eq(Constants.SERVICE_NAME, "TOTAL"));
        if (Objects.nonNull(dashboard) && StringUtils.hasText(dashboard.getDashboardUrl())) {
            return Result.success(getDashboardUrl(clusterId, dashboard));
        } else {
            return Result.error("缺少集群总览");
        }
    }

    @Override
    public String getGrafanaHost(Integer clusterId) {
        String host = GlobalVariables.getValue(clusterId, "GRAFANA.Grafana.__hostIp__");
        return host + ":3000";
    }

    @Override
    public String getDashboardUrl(Integer clusterId, ClusterServiceDashboard dashboard) {
        String url = dashboard.getDashboardUrl();
        if (proxy) {
            // 兼容旧记录
            if (url.startsWith("http://${grafanaHost}:3000")) {
                url = url.substring(26);
            }
            return contextPath + GRAFANA_PATH + "/" + clusterId + url;
        } else {
            Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
            return PlaceholderUtils.replacePlaceholders(dashboard.getDashboardUrl(), globalVariables,
                    Constants.REGEX_VARIABLE);
        }
    }
}
