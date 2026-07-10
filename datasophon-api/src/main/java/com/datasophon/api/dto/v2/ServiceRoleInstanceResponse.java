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

import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 服务角色实例响应体（v2）。
 *
 * <p>字段对应前端 {@code ServiceRoleInstanceInfo}：
 * <ul>
 *   <li>{@code serviceRoleState} — 枚举 {@link ServiceRoleState#name()}（英文字符串）</li>
 *   <li>{@code serviceRoleStateCode} — 枚举 {@link ServiceRoleState#getValue()}（整数）</li>
 * </ul>
 */
@Data
public class ServiceRoleInstanceResponse {

    private Integer id;
    private Integer serviceId;
    private String serviceRoleName;
    private String hostname;
    private Integer roleGroupId;
    private String roleGroupName;
    /** 枚举英文名，如 "RUNNING" / "STOP" / "EXISTS_ALARM"。 */
    private String serviceRoleState;
    /** 枚举数值，如 1 / 2 / 3。 */
    private Integer serviceRoleStateCode;
    private Integer clusterId;
    private Date createTime;

    /**
     * 从实体构建响应体。实体的 {@code serviceRoleStateCode} 与 {@code roleGroupName}
     * 已由 service 层（{@code listAll}）填充。
     */
    public static ServiceRoleInstanceResponse from(ClusterServiceRoleInstanceEntity entity) {
        if (entity == null) {
            return null;
        }
        ServiceRoleInstanceResponse r = new ServiceRoleInstanceResponse();
        r.setId(entity.getId());
        r.setServiceId(entity.getServiceId());
        r.setServiceRoleName(entity.getServiceRoleName());
        r.setHostname(entity.getHostname());
        r.setRoleGroupId(entity.getRoleGroupId());
        r.setRoleGroupName(entity.getRoleGroupName());

        ServiceRoleState state = entity.getServiceRoleState();
        if (state != null) {
            r.setServiceRoleState(state.name());
            r.setServiceRoleStateCode(state.getValue());
        }

        r.setClusterId(entity.getClusterId());
        r.setCreateTime(entity.getCreateTime());
        return r;
    }

    public static List<ServiceRoleInstanceResponse> fromList(List<ClusterServiceRoleInstanceEntity> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(ServiceRoleInstanceResponse::from).toList();
    }
}
