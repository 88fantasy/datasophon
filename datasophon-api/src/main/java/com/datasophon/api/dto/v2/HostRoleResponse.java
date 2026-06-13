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

import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * 主机角色响应体。
 *
 * <p>{@code serviceRoleStateCode} 为整数状态码，
 * 来自 {@link ClusterServiceRoleInstanceEntity#getServiceRoleStateCode()}，
 * 供前端 {@code STATE_COLOR} map 使用。
 */
@Data
public class HostRoleResponse {
    
    private Integer id;
    private String serviceRoleName;
    private String hostname;
    /** 角色状态码（整数），1=运行中，2=停止，3=存在告警，4=退役中，5=已退役。 */
    private Integer serviceRoleStateCode;
    private Integer serviceId;
    private Integer clusterId;
    
    public static HostRoleResponse from(ClusterServiceRoleInstanceEntity entity) {
        HostRoleResponse r = new HostRoleResponse();
        r.setId(entity.getId());
        r.setServiceRoleName(entity.getServiceRoleName());
        r.setHostname(entity.getHostname());
        r.setServiceRoleStateCode(entity.getServiceRoleStateCode());
        r.setServiceId(entity.getServiceId());
        r.setClusterId(entity.getClusterId());
        return r;
    }
    
    public static List<HostRoleResponse> fromList(List<ClusterServiceRoleInstanceEntity> entities) {
        return entities.stream().map(HostRoleResponse::from).collect(Collectors.toList());
    }
}
