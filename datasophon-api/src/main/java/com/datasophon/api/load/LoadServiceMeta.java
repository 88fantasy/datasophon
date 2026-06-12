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

package com.datasophon.api.load;

import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.entity.FrameInfoEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.hutool.core.collection.CollUtil;

@Component
public class LoadServiceMeta implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadServiceMeta.class);
    
    @Autowired
    private FrameInfoService frameInfoService;
    
    @Autowired
    private ClusterVariableService variableService;
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private DdlMetaService ddlMetaService;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        loadGlobalVariables(clusters);
        
        Map<String, FrameInfoEntity> frameworkCache = new HashMap<>();
        
        MetaStorage metaStorage;
        try {
            metaStorage = StorageUtils.getMetaStorage();
        } catch (IllegalStateException e) {
            logger.warn("No MetaStorage available, skipping service meta load: {}", e.getMessage());
            return;
        }
        List<ServiceMetaItem> physicalDdlItems = metaStorage.listService(MetaStorage.PHYSICAL);
        Map<String, List<ServiceMetaItem>> groupedPhysicalMap = physicalDdlItems.stream().collect(Collectors.groupingBy(ServiceMetaItem::getFramework));
        groupedPhysicalMap.forEach((frameCode, items) -> {
            FrameInfoEntity frameInfo = frameworkCache.computeIfAbsent(frameCode, c -> frameInfoService.saveFrameIfAbsent(frameCode));
            for (ServiceMetaItem item : items) {
                try {
                    String serviceDdl = metaStorage.getServiceDdL(item);
                    ddlMetaService.loadServicePhysicalDdl(clusters, frameInfo, item.getServiceName(), serviceDdl);
                } catch (Exception e) {
                    logger.error("invalid service ddl file: {} {}", frameCode, item.getServiceName(), e);
                }
            }
        });
        List<ServiceMetaItem> k8sItems = metaStorage.listService(MetaStorage.K8S);
        Map<String, List<ServiceMetaItem>> groupedK8sMap = k8sItems.stream().collect(Collectors.groupingBy(ServiceMetaItem::getFramework));
        groupedK8sMap.forEach((frameCode, items) -> {
            FrameInfoEntity frameInfo = frameworkCache.computeIfAbsent(frameCode, c -> frameInfoService.saveFrameIfAbsent(frameCode));
            for (ServiceMetaItem item : items) {
                try {
                    String serviceDdl = metaStorage.getServiceDdL(item);
                    ddlMetaService.loadServiceK8sDdl(frameInfo, item.getServiceName(), serviceDdl);
                } catch (Exception e) {
                    logger.error("invalid service ddl file: {} {}", frameCode, item.getServiceName(), e);
                }
            }
        });
    }
    
    public void loadGlobalVariables(List<ClusterInfoEntity> clusters) {
        if (CollUtil.isNotEmpty(clusters)) {
            for (ClusterInfoEntity cluster : clusters) {
                ConcurrentHashMap<String, String> globalVariables = GlobalVariables.genDefaultGlobalVariables();
                List<ClusterVariable> variables = variableService.list(Wrappers.<ClusterVariable>lambdaQuery()
                        .eq(ClusterVariable::getClusterId, cluster.getId()));
                for (ClusterVariable variable : variables) {
                    globalVariables.put(GlobalVariables.surroundKey(variable.getServiceName() + "." + variable.getVariableName()), variable.getVariableValue());
                }
                globalVariables.put(GlobalVariables.surroundKey(GlobalVariables.CLUSTER_CODE), cluster.getClusterFrame());
                GlobalVariables.put(cluster.getId(), globalVariables);
            }
        }
    }
    
}
