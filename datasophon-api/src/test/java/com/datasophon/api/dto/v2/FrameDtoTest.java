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

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.vo.frameinfo.FrameInfoVO;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.enums.RoleType;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 纯 Java 单测（无 Spring），验证 frame 域 DTO 的字段映射与内部字段屏蔽。
 */
class FrameDtoTest {
    
    // ─── FrameServiceItemResponse.from() ────────────────────────────────────
    
    @Test
    void serviceItem_from_onlyExposedFields() {
        FrameServiceEntity entity = new FrameServiceEntity();
        entity.setId(1);
        entity.setFrameId(10);
        entity.setFrameCode("BIGDATA-physical");
        entity.setServiceName("HDFS");
        entity.setLabel("HDFS 分布式文件系统");
        entity.setServiceVersion("3.3.4");
        entity.setServiceDesc("Hadoop 分布式文件系统");
        entity.setInstalled(true);
        // 内部字段 — 应不出现在 DTO 中
        entity.setServiceJson("{\"name\":\"HDFS\"}");
        entity.setConfigFileJson("{\"config\":\"value\"}");
        entity.setServiceJsonMd5("abc123");
        
        FrameServiceItemResponse resp = FrameServiceItemResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(1);
        assertThat(resp.getFrameId()).isEqualTo(10);
        assertThat(resp.getFrameCode()).isEqualTo("BIGDATA-physical");
        assertThat(resp.getServiceName()).isEqualTo("HDFS");
        assertThat(resp.getLabel()).isEqualTo("HDFS 分布式文件系统");
        assertThat(resp.getServiceVersion()).isEqualTo("3.3.4");
        assertThat(resp.getServiceDesc()).isEqualTo("Hadoop 分布式文件系统");
        assertThat(resp.getInstalled()).isTrue();
        // DTO 类上没有 serviceJson / configFileJson / serviceJsonMd5 字段
        // 通过反射确认这些字段不存在
        assertThat(resp.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("serviceJson", "configFileJson", "serviceJsonMd5",
                        "arch", "dependencies", "serviceConfig", "configFileJsonMd5",
                        "decompressPackageName", "sortNum", "type");
    }
    
    @Test
    void serviceItem_fromList_null_returnsEmptyList() {
        assertThat(FrameServiceItemResponse.fromList(null)).isEmpty();
    }
    
    @Test
    void serviceItem_fromList_mapsAll() {
        FrameServiceEntity e1 = new FrameServiceEntity();
        e1.setId(1);
        e1.setServiceName("HDFS");
        FrameServiceEntity e2 = new FrameServiceEntity();
        e2.setId(2);
        e2.setServiceName("YARN");
        
        List<FrameServiceItemResponse> result = FrameServiceItemResponse.fromList(List.of(e1, e2));
        
        assertThat(result).hasSize(2);
        assertThat(result).extracting("serviceName").containsExactly("HDFS", "YARN");
    }
    
    // ─── FrameK8sServiceItemResponse.from() ─────────────────────────────────
    
    @Test
    void k8sServiceItem_from_onlyExposedFields() {
        FrameK8sServiceEntity entity = new FrameK8sServiceEntity();
        entity.setId(5);
        entity.setFrameId(20);
        entity.setServiceName("APISIX");
        entity.setServiceVersion("3.9.0");
        entity.setServiceDesc("API 网关");
        entity.setSupportArtifacts(List.of("helm", "kustomize"));
        // 内部字段
        entity.setManifestJson("{\"kind\":\"HelmRelease\"}");
        entity.setArtifact("oci://registry/apisix:3.9.0");
        
        FrameK8sServiceItemResponse resp = FrameK8sServiceItemResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(5);
        assertThat(resp.getFrameId()).isEqualTo(20);
        assertThat(resp.getServiceName()).isEqualTo("APISIX");
        assertThat(resp.getServiceVersion()).isEqualTo("3.9.0");
        assertThat(resp.getServiceDesc()).isEqualTo("API 网关");
        assertThat(resp.getSupportArtifacts()).containsExactly("helm", "kustomize");
        // DTO 没有 manifestJson / artifact 字段
        assertThat(resp.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("manifestJson", "artifact", "dependencies",
                        "type", "runtime", "selected", "metaFileType", "namespace");
    }
    
    @Test
    void k8sServiceItem_fromList_null_returnsEmptyList() {
        assertThat(FrameK8sServiceItemResponse.fromList(null)).isEmpty();
    }
    
    // ─── FrameWithServicesResponse.from() ───────────────────────────────────
    
    @Test
    void frameWithServices_from_nullServiceLists_returnsEmptyLists() {
        FrameInfoVO vo = new FrameInfoVO();
        vo.setId(1);
        vo.setFrameName("大数据框架");
        vo.setFrameCode("BIGDATA-physical");
        vo.setFrameVersion("3.3.4");
        // frameServiceList 和 frameK8sServiceList 均为 null
        
        FrameWithServicesResponse resp = FrameWithServicesResponse.from(vo);
        
        assertThat(resp.getId()).isEqualTo(1);
        assertThat(resp.getFrameName()).isEqualTo("大数据框架");
        assertThat(resp.getFrameCode()).isEqualTo("BIGDATA-physical");
        assertThat(resp.getFrameVersion()).isEqualTo("3.3.4");
        // 不抛 NPE，返回空列表
        assertThat(resp.getFrameServiceList()).isNotNull().isEmpty();
        assertThat(resp.getFrameK8sServiceList()).isNotNull().isEmpty();
    }
    
    @Test
    void frameWithServices_from_nestedServicesConverted() {
        FrameServiceEntity svc = new FrameServiceEntity();
        svc.setId(10);
        svc.setServiceName("HDFS");
        svc.setServiceJson("{\"internal\":true}");
        
        FrameInfoVO vo = new FrameInfoVO();
        vo.setId(2);
        vo.setFrameName("物理集群");
        vo.setFrameCode("BIGDATA-physical");
        vo.setFrameVersion("3.3.4");
        vo.setFrameServiceList(List.of(svc));
        
        FrameWithServicesResponse resp = FrameWithServicesResponse.from(vo);
        
        assertThat(resp.getFrameServiceList()).hasSize(1);
        assertThat(resp.getFrameServiceList().get(0).getServiceName()).isEqualTo("HDFS");
        assertThat(resp.getFrameServiceList().get(0).getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("serviceJson");
    }
    
    @Test
    void frameWithServices_fromList_null_returnsEmptyList() {
        assertThat(FrameWithServicesResponse.fromList(null)).isEmpty();
    }
    
    // ─── FrameServiceItemResponse.selected 字段映射（addService 上下文）──────
    
    @Test
    void serviceItem_from_selected_isMapped() {
        FrameServiceEntity entity = new FrameServiceEntity();
        entity.setId(5);
        entity.setServiceName("KAFKA");
        entity.setSelected(true);
        entity.setInstalled(false);
        
        FrameServiceItemResponse resp = FrameServiceItemResponse.from(entity);
        
        assertThat(resp.getSelected()).isTrue();
    }
    
    @Test
    void serviceItem_from_selected_nullWhenNotSet() {
        FrameServiceEntity entity = new FrameServiceEntity();
        entity.setId(6);
        entity.setServiceName("SPARK3");
        
        FrameServiceItemResponse resp = FrameServiceItemResponse.from(entity);
        
        assertThat(resp.getSelected()).isNull();
    }
    
    // ─── FrameServiceRoleItemResponse.from() ────────────────────────────────
    
    @Test
    void serviceRoleItem_from_mapsExposedFields() {
        FrameServiceRoleEntity entity = new FrameServiceRoleEntity();
        entity.setId(100);
        entity.setServiceId(10);
        entity.setServiceRoleName("NameNode");
        entity.setServiceRoleType(RoleType.MASTER);
        entity.setCardinality("1");
        // 内部字段 — 不应出现在 DTO 中
        entity.setServiceRoleJson("{\"role\":\"NameNode\"}");
        entity.setServiceRoleJsonMd5("deadbeef");
        
        FrameServiceRoleItemResponse resp = FrameServiceRoleItemResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(100);
        assertThat(resp.getServiceId()).isEqualTo(10);
        assertThat(resp.getServiceRoleName()).isEqualTo("NameNode");
        assertThat(resp.getServiceRoleType()).isEqualTo("master");
        assertThat(resp.getCardinality()).isEqualTo("1");
        // 确认内部字段未暴露
        assertThat(resp.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("serviceRoleJson", "serviceRoleJsonMd5", "jmxPort", "logFile", "sortNum", "frameCode");
    }
    
    @Test
    void serviceRoleItem_from_null_returnsNull() {
        assertThat(FrameServiceRoleItemResponse.from(null)).isNull();
    }
    
    @Test
    void serviceRoleItem_fromList_null_returnsEmpty() {
        assertThat(FrameServiceRoleItemResponse.fromList(null)).isEmpty();
    }
    
    @Test
    void serviceRoleItem_from_serviceRoleType_usesDescNotEnumName() {
        FrameServiceRoleEntity entity = new FrameServiceRoleEntity();
        entity.setId(200);
        entity.setServiceRoleType(RoleType.WORKER);
        
        FrameServiceRoleItemResponse resp = FrameServiceRoleItemResponse.from(entity);
        
        // 前端期望小写字符串("worker")，不是枚举名("WORKER")
        assertThat(resp.getServiceRoleType()).isEqualTo("worker");
        assertThat(resp.getServiceRoleType()).isNotEqualTo("WORKER");
    }
}
