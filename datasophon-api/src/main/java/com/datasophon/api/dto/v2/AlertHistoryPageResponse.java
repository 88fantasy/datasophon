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

import com.datasophon.dao.entity.ClusterAlertHistory;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.Data;

/**
 * 告警历史分页响应体（v2）。
 */
@Data
public class AlertHistoryPageResponse {

    private List<AlertHistoryResponse> totalList;
    private Long totalCount;

    public static AlertHistoryPageResponse from(IPage<ClusterAlertHistory> page) {
        AlertHistoryPageResponse response = new AlertHistoryPageResponse();
        response.setTotalList(AlertHistoryResponse.fromList(page.getRecords()));
        response.setTotalCount(page.getTotal());
        return response;
    }
}
