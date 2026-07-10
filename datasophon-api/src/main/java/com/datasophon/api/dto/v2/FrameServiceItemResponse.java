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

import com.datasophon.dao.entity.FrameServiceEntity;

import java.util.Collections;
import java.util.List;

import lombok.Data;

/**
 * 物理机框架服务响应 DTO，只暴露前端所需字段，屏蔽内部字段（serviceJson、configFileJson 等）。
 */
@Data
public class FrameServiceItemResponse {

    private Integer id;
    private Integer frameId;
    private String frameCode;
    private String serviceName;
    private String label;
    private String serviceVersion;
    private String serviceDesc;
    private Boolean installed;
    /** 部署清单中是否被勾选（addService 向导上下文，非清单场景为 null）。 */
    private Boolean selected;

    public static FrameServiceItemResponse from(FrameServiceEntity entity) {
        if (entity == null) {
            return null;
        }
        FrameServiceItemResponse r = new FrameServiceItemResponse();
        r.setId(entity.getId());
        r.setFrameId(entity.getFrameId());
        r.setFrameCode(entity.getFrameCode());
        r.setServiceName(entity.getServiceName());
        r.setLabel(entity.getLabel());
        r.setServiceVersion(entity.getServiceVersion());
        r.setServiceDesc(entity.getServiceDesc());
        r.setInstalled(entity.getInstalled());
        r.setSelected(entity.getSelected());
        return r;
    }

    public static List<FrameServiceItemResponse> fromList(List<FrameServiceEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(FrameServiceItemResponse::from).toList();
    }
}
