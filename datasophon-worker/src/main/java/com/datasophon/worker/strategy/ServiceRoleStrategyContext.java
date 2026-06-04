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

package com.datasophon.worker.strategy;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRoleStrategyContext {
    
    private static final Map<String, ServiceRoleStrategy> map = new ConcurrentHashMap<>();
    
    static {
        map.put("NameNode", new NameNodeHandlerStrategy("HDFS", "NameNode"));
        map.put("ZKFC", new ZKFCHandlerStrategy("HDFS", "ZKFC"));
        map.put("JournalNode", new JournalNodeHandlerStrategy("HDFS", "JournalNode"));
        map.put("DataNode", new DataNodeHandlerStrategy("HDFS", "DataNode"));
        map.put("ResourceManager", new ResourceManagerHandlerStrategy("YARN", "ResourceManager"));
        map.put("NodeManager", new NodeManagerHandlerStrategy("YARN", "NodeManager"));
        map.put("RangerAdmin", new RangerAdminHandlerStrategy("RANGER", "RangerAdmin"));
        map.put("HiveMetaStore", new HiveMetaStoreHandlerStrategy("HIVE", "HiveMetaStore"));
        map.put("HiveServer2", new HiveServer2HandlerStrategy("HIVE", "HiveServer2"));
        map.put("HbaseMaster", new HbaseHandlerStrategy("HBASE", "HbaseMaster"));
        map.put("RegionServer", new HbaseHandlerStrategy("HBASE", "RegionServer"));
        map.put("Krb5Kdc", new Krb5KdcHandlerStrategy("KERBEROS", "Krb5Kdc"));
        map.put("KAdmin", new KAdminHandlerStrategy("KERBEROS", "KAdmin"));
        map.put("SRFE", new FEHandlerStrategy("STARROCKS", "SRFE"));
        map.put("DorisFE", new FEHandlerStrategy("DORIS", "DorisFE"));
        map.put("DorisFEObserver", new FEObserverHandlerStrategy("DORIS", "DorisFEObserver"));
        map.put("ZkServer", new ZkServerHandlerStrategy("ZOOKEEPER", "ZkServer"));
        map.put("KafkaBroker", new KafkaHandlerStrategy("KAFKA", "KafkaBroker"));
        map.put("SRBE", new BEHandlerStrategy("STARROCKS", "SRBE"));
        map.put("DorisBE", new BEHandlerStrategy("DORIS", "DorisBE"));
        map.put("HistoryServer", new HistoryServerHandlerStrategy("YARN", "HistoryServer"));
        // TEZ Server service
        map.put("TezServer", new TezServerHandlerStrategy("TEZ", "TezServer"));
        // kyuubi
        map.put("KyuubiServer", new KyuubiServerHandlerStrategy("KYUUBI", "KyuubiServer"));
        // flink
        map.put("FlinkClient", new FlinkHandlerStrategy("FLINK", "FlinkClient"));
        // spark3
        map.put("SparkThriftServer", new SparkThriftHandlerStrategy("SPARK3", "SparkThriftServer"));
        // uscheduler
        map.put("UMasterServer", new DSMasterHandlerStrategy("USCHEDULER", "UMasterServer"));
        // DolphinScheduler
        map.put("MasterServer", new DSMasterHandlerStrategy("DS", "MasterServer"));
        // nacos
        map.put("NacosServer", new NacosMasterHandlerStrategy("NACOS", "NacosServer"));
        
        // apisix
        map.put("Apisix", new ApisixHandlerStrategy("APISIX", "Apisix"));
        
        // nginx
        map.put("Nginx", new NginxHandlerStrategy("NGINX", "Nginx"));
    }
    
    public static ServiceRoleStrategy getServiceRoleHandler(String type) {
        if (StringUtils.isBlank(type)) {
            return null;
        }
        return map.get(type);
    }
}
