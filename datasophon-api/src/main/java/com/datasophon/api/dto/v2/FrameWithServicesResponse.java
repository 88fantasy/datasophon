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

import com.datasophon.api.vo.frameinfo.FrameInfoVO;

import java.util.Collections;
import java.util.List;

import lombok.Data;

/**
 * 框架 + 嵌套服务列表响应 DTO，对应 GET /v2/frame/services 返回结构。
 * 屏蔽 FrameServiceEntity / FrameK8sServiceEntity 的内部字段。
 */
@Data
public class FrameWithServicesResponse {

    private Integer id;
    private String frameName;
    private String frameCode;
    private String frameVersion;
    private List<FrameServiceItemResponse> frameServiceList;
    private List<FrameK8sServiceItemResponse> frameK8sServiceList;

    public static FrameWithServicesResponse from(FrameInfoVO vo) {
        if (vo == null) {
            return null;
        }
        FrameWithServicesResponse r = new FrameWithServicesResponse();
        r.setId(vo.getId());
        r.setFrameName(vo.getFrameName());
        r.setFrameCode(vo.getFrameCode());
        r.setFrameVersion(vo.getFrameVersion());
        r.setFrameServiceList(
                vo.getFrameServiceList() == null
                        ? Collections.emptyList()
                        : FrameServiceItemResponse.fromList(vo.getFrameServiceList()));
        r.setFrameK8sServiceList(
                vo.getFrameK8sServiceList() == null
                        ? Collections.emptyList()
                        : FrameK8sServiceItemResponse.fromList(vo.getFrameK8sServiceList()));
        return r;
    }

    public static List<FrameWithServicesResponse> fromList(List<FrameInfoVO> vos) {
        if (vos == null) {
            return Collections.emptyList();
        }
        return vos.stream().map(FrameWithServicesResponse::from).toList();
    }
}
