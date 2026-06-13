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
import com.datasophon.dao.enums.AlertLevel;

import jakarta.validation.constraints.NotBlank;

import java.util.Arrays;

import lombok.Data;

/**
 * 新建告警指标请求体（v2）。
 */
@Data
public class SaveAlertQuotaRequest {
    
    @NotBlank
    private String alertQuotaName;
    
    private String alertExpr;
    
    private String compareMethod;
    
    private Long alertThreshold;
    
    /** 告警级别，前端传字符串（"warning" / "exception"），对应 AlertLevel.desc */
    private String alertLevel;
    
    private Integer alertGroupId;
    
    private String serviceRoleName;
    
    private Integer noticeGroupId;
    
    private Integer alertTactic;
    
    private Integer intervalDuration;
    
    private Integer triggerDuration;
    
    private String alertAdvice;
    
    public ClusterAlertQuota toEntity() {
        ClusterAlertQuota e = new ClusterAlertQuota();
        e.setAlertQuotaName(alertQuotaName);
        e.setAlertExpr(alertExpr);
        e.setCompareMethod(compareMethod);
        e.setAlertThreshold(alertThreshold);
        e.setAlertLevel(resolveAlertLevel(alertLevel));
        e.setAlertGroupId(alertGroupId);
        e.setServiceRoleName(serviceRoleName);
        e.setNoticeGroupId(noticeGroupId);
        e.setAlertTactic(alertTactic);
        e.setIntervalDuration(intervalDuration);
        e.setTriggerDuration(triggerDuration);
        e.setAlertAdvice(alertAdvice);
        return e;
    }
    
    private static AlertLevel resolveAlertLevel(String desc) {
        if (desc == null) {
            return null;
        }
        return Arrays.stream(AlertLevel.values())
                .filter(l -> l.getDesc().equalsIgnoreCase(desc))
                .findFirst()
                .orElse(null);
    }
}
