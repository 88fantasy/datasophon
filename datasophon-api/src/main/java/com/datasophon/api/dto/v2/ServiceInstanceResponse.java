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

package com.datasophon.api.dto.v2;

import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceState;

import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 服务实例响应体（v2）。
 *
 * <p>屏蔽 {@link ClusterServiceInstanceEntity} 的实体细节：
 * <ul>
 *   <li>{@code serviceState} — 枚举 {@link ServiceState#name()}（英文字符串，如 "RUNNING"）</li>
 *   <li>{@code serviceStateCode} — 枚举 {@link ServiceState#getValue()}（整数，如 2）</li>
 *   <li>{@code needRestart} — {@link NeedRestart#YES} 时为 {@code true}</li>
 * </ul>
 */
@Data
public class ServiceInstanceResponse {

    private Integer id;
    private Integer clusterId;
    private String serviceName;
    private String label;
    /** 枚举英文名，如 "RUNNING" / "WAIT_INSTALL" / "EXISTS_ALARM" / "EXISTS_EXCEPTION"。 */
    private String serviceState;
    /** 枚举数值，如 1 / 2 / 3 / 4。 */
    private Integer serviceStateCode;
    /** 是否需要重启。 */
    private Boolean needRestart;
    private Integer frameServiceId;
    private Integer sortNum;
    private String dashboardUrl;
    private Integer alertNum;
    private String catalog;
    private Date createTime;
    private Date updateTime;

    /**
     * 从实体构建响应体，转换枚举字段。
     */
    public static ServiceInstanceResponse from(ClusterServiceInstanceEntity entity) {
        if (entity == null) {
            return null;
        }
        ServiceInstanceResponse r = new ServiceInstanceResponse();
        r.setId(entity.getId());
        r.setClusterId(entity.getClusterId());
        r.setServiceName(entity.getServiceName());
        r.setLabel(entity.getLabel());

        ServiceState state = entity.getServiceState();
        if (state != null) {
            r.setServiceState(state.name());
            r.setServiceStateCode(state.getValue());
        }

        NeedRestart nr = entity.getNeedRestart();
        r.setNeedRestart(nr == NeedRestart.YES);

        r.setFrameServiceId(entity.getFrameServiceId());
        r.setSortNum(entity.getSortNum());
        r.setDashboardUrl(entity.getDashboardUrl());
        r.setAlertNum(entity.getAlertNum());
        r.setCatalog(entity.getCatalog());
        r.setCreateTime(entity.getCreateTime());
        r.setUpdateTime(entity.getUpdateTime());
        return r;
    }

    public static List<ServiceInstanceResponse> fromList(List<ClusterServiceInstanceEntity> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(ServiceInstanceResponse::from).toList();
    }
}
