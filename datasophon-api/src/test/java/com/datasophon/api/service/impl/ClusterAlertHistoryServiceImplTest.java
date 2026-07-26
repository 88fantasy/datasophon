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

package com.datasophon.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.mapper.ClusterAlertHistoryMapper;

import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

@ExtendWith(MockitoExtension.class)
class ClusterAlertHistoryServiceImplTest {

    @Mock
    private ClusterAlertHistoryMapper alertHistoryMapper;

    @InjectMocks
    private ClusterAlertHistoryServiceImpl service;

    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ClusterAlertHistory.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", alertHistoryMapper);
    }

    @Test
    void getHistoryPage_withoutStatus_returnsAllStatusesForCurrentCluster() {
        stubQueryResult();

        IPage<ClusterAlertHistory> result =
                service.getHistoryPage(7, null, null, null, null, null, null, 1, 20);

        LambdaQueryWrapper<ClusterAlertHistory> wrapper = captureQueryWrapper();
        assertThat(wrapper.getSqlSegment())
                .contains("cluster_id", "create_time", "id")
                .contains("limit 0,20")
                .doesNotContain("is_enabled");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(7);
        assertThat(result.getTotal()).isEqualTo(97);
        assertThat(result.getRecords()).extracting(ClusterAlertHistory::getId).containsExactly(1);
    }

    @Test
    void getHistoryPage_appliesEveryOptionalFilter() {
        stubQueryResult();
        Date startTime = new Date(1_000L);
        Date endTime = new Date(2_000L);

        service.getHistoryPage(7, "NameNode", "node-1", AlertLevel.WARN, 2,
                startTime, endTime, 3, 50);

        LambdaQueryWrapper<ClusterAlertHistory> wrapper = captureQueryWrapper();
        assertThat(wrapper.getSqlSegment())
                .contains("alert_target_name", "hostname", "alert_level", "is_enabled", "create_time");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(7, "%NameNode%", "%node-1%", AlertLevel.WARN, 2, startTime, endTime);
        assertThat(wrapper.getSqlSegment()).contains("limit 100,50");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaQueryWrapper<ClusterAlertHistory> captureQueryWrapper() {
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(alertHistoryMapper).selectList(captor.capture());
        return (LambdaQueryWrapper<ClusterAlertHistory>) captor.getValue();
    }

    private void stubQueryResult() {
        when(alertHistoryMapper.selectCount(any(Wrapper.class))).thenReturn(97L);
        when(alertHistoryMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(ClusterAlertHistory.builder().id(1).build()));
    }
}
