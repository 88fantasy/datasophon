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

import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;

import java.util.List;

import lombok.Data;

/**
 * 角色组响应体（v2）。
 *
 * <p>对应前端 {@code { id: number; roleGroupName: string; serviceInstanceId: number }}。
 */
@Data
public class RoleGroupResponse {

    private Integer id;
    private String roleGroupName;
    private Integer serviceInstanceId;

    public static RoleGroupResponse from(ClusterServiceInstanceRoleGroup entity) {
        if (entity == null) {
            return null;
        }
        RoleGroupResponse r = new RoleGroupResponse();
        r.setId(entity.getId());
        r.setRoleGroupName(entity.getRoleGroupName());
        r.setServiceInstanceId(entity.getServiceInstanceId());
        return r;
    }

    public static List<RoleGroupResponse> fromList(List<ClusterServiceInstanceRoleGroup> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(RoleGroupResponse::from).toList();
    }
}
