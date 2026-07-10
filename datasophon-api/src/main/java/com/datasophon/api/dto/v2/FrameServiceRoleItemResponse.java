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

import com.datasophon.dao.entity.FrameServiceRoleEntity;

import java.util.Collections;
import java.util.List;

import lombok.Data;

/**
 * 框架服务角色响应 DTO，只暴露前端 addService 向导所需字段，
 * 屏蔽内部字段（serviceRoleJson、serviceRoleJsonMd5、logFile、sortNum 等）。
 */
@Data
public class FrameServiceRoleItemResponse {

    private Integer id;
    private Integer serviceId;
    private String serviceRoleName;
    /** 角色类型："master" / "worker" / "client" / "slave"，对应前端 FrameServiceRole.serviceRoleType。 */
    private String serviceRoleType;
    private String cardinality;
    /** 部署清单回填的主机列表（非清单场景为 null）。 */
    private List<String> hosts;

    public static FrameServiceRoleItemResponse from(FrameServiceRoleEntity entity) {
        if (entity == null) {
            return null;
        }
        FrameServiceRoleItemResponse r = new FrameServiceRoleItemResponse();
        r.setId(entity.getId());
        r.setServiceId(entity.getServiceId());
        r.setServiceRoleName(entity.getServiceRoleName());
        r.setServiceRoleType(entity.getServiceRoleType() != null
                ? entity.getServiceRoleType().getDesc()
                : null);
        r.setCardinality(entity.getCardinality());
        r.setHosts(entity.getHosts());
        return r;
    }

    public static List<FrameServiceRoleItemResponse> fromList(List<FrameServiceRoleEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(FrameServiceRoleItemResponse::from).toList();
    }
}
