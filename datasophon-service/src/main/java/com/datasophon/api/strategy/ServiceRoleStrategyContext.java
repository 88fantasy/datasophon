/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.api.strategy;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRoleStrategyContext {
    
    private static final Map<String, ServiceRoleStrategy> strategyMap = new ConcurrentHashMap<>();
    

    static {
        strategyMap.put("NameNode", new NameNodeHandlerStrategy());
        strategyMap.put("ResourceManager", new RMHandlerStrategy());
        strategyMap.put("HiveServer2", new HiveServer2HandlerStrategy());
        strategyMap.put("ZkServer", new ZkServerHandlerStrategy());
        strategyMap.put("JournalNode", new JournalNodeHandlerStrategy());
        strategyMap.put("ZKFC", new ZKFCHandlerStrategy());
        strategyMap.put("SRFE", new FEHandlerStrategy());
        strategyMap.put("DorisFE", new FEHandlerStrategy());
        strategyMap.put("DorisFEObserver", new FEObserverHandlerStartegy());
        strategyMap.put("SRBE", new BEHandlerStartegy());
        strategyMap.put("DorisBE", new BEHandlerStartegy());
        strategyMap.put("Krb5Kdc", new Krb5KdcHandlerStrategy());
        strategyMap.put("KAdmin", new KAdminHandlerStrategy());
        strategyMap.put("RangerAdmin", new RangerAdminHandlerStrategy());
        strategyMap.put("ElasticSearch", new ElasticSearchHandlerStrategy());
        strategyMap.put("AlertManager", new AlertManagerHandlerStrategy());
        strategyMap.put("KyuubiServer", new KyuubiServerHandlerStrategy());
        strategyMap.put("RANGER", new RangerAdminHandlerStrategy());
        strategyMap.put("ZOOKEEPER", new ZkServerHandlerStrategy());
        strategyMap.put("YARN", new RMHandlerStrategy());
        strategyMap.put("HDFS", new NameNodeHandlerStrategy());
        strategyMap.put("HIVE", new HiveServer2HandlerStrategy());
        strategyMap.put("KafkaBroker", new KafkaHandlerStrategy());
        strategyMap.put("HBASE", new HBaseHandlerStrategy());
        strategyMap.put("FLINK", new FlinkHandlerStrategy());
        strategyMap.put("Etcd", new EtcdHandlerStrategy());

    }

    public static ServiceRoleStrategy getServiceRoleHandler(String type) {
        if (StringUtils.isBlank(type)) {
            return null;
        }
        return strategyMap.get(type);
    }


}
