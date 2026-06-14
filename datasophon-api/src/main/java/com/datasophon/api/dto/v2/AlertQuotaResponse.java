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

import com.datasophon.dao.entity.ClusterAlertQuota;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * 告警指标响应体（v2）。屏蔽实体直接暴露。
 */
@Data
public class AlertQuotaResponse {
    
    private Integer id;
    private String alertQuotaName;
    private String alertExpr;
    private String compareMethod;
    private Long alertThreshold;
    /** 序列化为 AlertLevel.desc（"warning" / "exception"） */
    private String alertLevel;
    private Integer alertGroupId;
    private String alertGroupName;
    private String serviceRoleName;
    private Integer noticeGroupId;
    private Integer alertTactic;
    private Integer intervalDuration;
    private Integer triggerDuration;
    private String alertAdvice;
    /** 序列化为 QuotaState.desc（"启用" / "未启用" / "待更新"） */
    private String quotaState;
    private Integer quotaStateCode;
    
    public static AlertQuotaResponse from(ClusterAlertQuota entity) {
        AlertQuotaResponse r = new AlertQuotaResponse();
        r.setId(entity.getId());
        r.setAlertQuotaName(entity.getAlertQuotaName());
        r.setAlertExpr(entity.getAlertExpr());
        r.setCompareMethod(entity.getCompareMethod());
        r.setAlertThreshold(entity.getAlertThreshold());
        r.setAlertLevel(entity.getAlertLevel() != null ? entity.getAlertLevel().getDesc() : null);
        r.setAlertGroupId(entity.getAlertGroupId());
        r.setAlertGroupName(entity.getAlertGroupName());
        r.setServiceRoleName(entity.getServiceRoleName());
        r.setNoticeGroupId(entity.getNoticeGroupId());
        r.setAlertTactic(entity.getAlertTactic());
        r.setIntervalDuration(entity.getIntervalDuration());
        r.setTriggerDuration(entity.getTriggerDuration());
        r.setAlertAdvice(entity.getAlertAdvice());
        r.setQuotaState(entity.getQuotaState() != null ? entity.getQuotaState().getDesc() : null);
        r.setQuotaStateCode(entity.getQuotaStateCode());
        return r;
    }
    
    public static List<AlertQuotaResponse> fromList(List<ClusterAlertQuota> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(AlertQuotaResponse::from).collect(Collectors.toList());
    }
}
