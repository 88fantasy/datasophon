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

import com.datasophon.dao.entity.FrameInfoEntity;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

/** 框架版本响应体。 */
@Data
public class FrameResponse {
    
    private Integer id;
    private String frameName;
    private String frameCode;
    private String frameVersion;
    
    public static FrameResponse from(FrameInfoEntity entity) {
        FrameResponse r = new FrameResponse();
        r.setId(entity.getId());
        r.setFrameName(entity.getFrameName());
        r.setFrameCode(entity.getFrameCode());
        r.setFrameVersion(entity.getFrameVersion());
        return r;
    }
    
    public static List<FrameResponse> fromList(List<FrameInfoEntity> entities) {
        return entities.stream().map(FrameResponse::from).collect(Collectors.toList());
    }
}
