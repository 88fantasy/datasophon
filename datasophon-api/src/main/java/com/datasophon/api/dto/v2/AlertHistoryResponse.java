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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 告警历史响应体（v2）。
 */
@Data
public class AlertHistoryResponse {

    private Integer id;
    private String alertGroupName;
    private String alertTargetName;
    private String alertInfo;
    private String alertAdvice;
    private String hostname;
    private String alertLevel;
    private Integer alertLevelCode;
    private String status;
    private Integer statusCode;
    private Date createTime;

    public static AlertHistoryResponse from(ClusterAlertHistory entity) {
        AlertHistoryResponse response = new AlertHistoryResponse();
        response.setId(entity.getId());
        response.setAlertGroupName(entity.getAlertGroupName());
        response.setAlertTargetName(entity.getAlertTargetName());
        response.setAlertInfo(entity.getAlertInfo());
        response.setAlertAdvice(entity.getAlertAdvice());
        response.setHostname(entity.getHostname());
        if (entity.getAlertLevel() != null) {
            response.setAlertLevel(entity.getAlertLevel().getDesc());
            response.setAlertLevelCode(entity.getAlertLevel().getValue());
        }
        response.setStatusCode(entity.getIsEnabled());
        response.setStatus(toStatus(entity.getIsEnabled()));
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

    public static List<AlertHistoryResponse> fromList(List<ClusterAlertHistory> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(AlertHistoryResponse::from).toList();
    }

    private static String toStatus(Integer statusCode) {
        if (Integer.valueOf(1).equals(statusCode)) {
            return "firing";
        }
        if (Integer.valueOf(2).equals(statusCode)) {
            return "resolved";
        }
        return null;
    }
}
