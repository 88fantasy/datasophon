package com.datasophon.api.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.UniEngineService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ClusterConfig;
import com.datasophon.common.model.GlobalConfig;
import com.datasophon.common.model.uni.*;
import com.datasophon.common.utils.PasswordSupport;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("uniEngineService")
public class UniEngineServiceImpl implements UniEngineService {

    private static final Logger logger = LoggerFactory.getLogger(UniEngineServiceImpl.class);

    @Autowired
    ClusterServiceRoleInstanceService clusterServiceRoleInstanceService;

    @Autowired
    ClusterVariableService clusterVariableService;

    @Autowired
    ClusterInfoService clusterInfoService;

    @Autowired
    private ClusterConfig clusterSampleConfig;

    @Override
    public Result getEngineInfo() {

        Result result = clusterInfoService.getClusterList();
        List<ClusterInfoEntity> clusterList = (List<ClusterInfoEntity>) result.getData();
        if (CollectionUtils.isEmpty(clusterList)) {
            return Result.error("vos集群列表为空");
        }
        Integer clusterId = clusterList.get(0).getId();

        EngineInfo engineInfo = new EngineInfo();
        engineInfo.setClusterId(clusterId);
        engineInfo.setEngineType("OFFLINE_ENGINE");

        //离线引擎
        List<ClusterServiceRoleInstanceEntity> dsApiServers = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "UApiServer");
        if (CollectionUtils.isNotEmpty(dsApiServers)) {
            engineInfo.setUSchedulerAddress(String.format("http://%s:%s", dsApiServers.get(0).getHostname(), "12345"));
            engineInfo.setUSchedulerUserName("admin");
            engineInfo.setUSchedulerUserPassword("dolphinscheduler123");
            logger.info("USCHEDULER(ds) get info success");
        } else {
            return Result.error("USCHEDULER(ds) api instances cannot be found");
        }
        List<ClusterServiceRoleInstanceEntity> easyflowServers = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "EasyflowServer");
        if (CollectionUtils.isNotEmpty(easyflowServers)) {
            engineInfo.setEasyFlowService(String.format("http://%s:%s", easyflowServers.get(0).getHostname(), "7070"));
            logger.info("EasyflowServer get info success");
        } else {
            return Result.error("EasyflowServer instances cannot be found");
        }

        // 实时引擎
        List<ClusterServiceRoleInstanceEntity> ustreamServers = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "UstreamServer");
        if (CollectionUtils.isNotEmpty(ustreamServers)) {
            Map<String, String> ustreamVars = getClusterVarsMap(clusterId, "USTREAM");
            engineInfo.setRealTimeSchedulerAddress(String.format("http://%s:%s", ustreamServers.get(0).getHostname(), "9112"));
            engineInfo.setRealTimeSchedulerUserName(ustreamVars.getOrDefault("${loginUsername}", "admin"));
            engineInfo.setRealTimeSchedulerUserPassword(ustreamVars.get("${loginPassword}"));
            engineInfo.setEngineType("OFF_REAL_TIME_ENGINE");
            logger.info("UstreamServer get info success");
        } else {
            logger.warn("UstreamServer instances cannot be found");
        }

        // 数据源
        engineInfo.setMysqlDatasource(getMysqlDatasource());
        engineInfo.setHiveDatasource(getHiveDatasource(clusterId));
        engineInfo.setPaimonDatasource(getPaimonDatasource(clusterId));
        engineInfo.setDorisDatasource(getDorisDatasource(clusterId));
        engineInfo.setKafkaDatasource(getKafkaDatasource(clusterId));

        String data = JSON.toJSONString(engineInfo);
        return Result.success().put(Constants.DATA, PasswordSupport.encryptDbPassword(data));

    }

    public MysqlDatasource getMysqlDatasource() {
        MysqlDatasource mysqlDatasource = new MysqlDatasource();
        GlobalConfig.MysqlConfig mysqlInfo = clusterSampleConfig.getGlobal().getMysql();
        mysqlDatasource.setHost(mysqlInfo.getHost().getIp());
        mysqlDatasource.setPort("3306");
        mysqlDatasource.setUserName("root");
        mysqlDatasource.setPassword(mysqlInfo.getPassword());
        JSONObject mysqlOther = new JSONObject();
        mysqlOther.put("allowPublicKeyRetrieval", true);
        mysqlOther.put("useSSL", false);
        mysqlDatasource.setOther(mysqlOther);
        logger.info("mysql get info success");

        return mysqlDatasource;
    }

    public HiveDatasource getHiveDatasource(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> hiveServer2s = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "HiveServer2");
        if (CollectionUtils.isNotEmpty(hiveServer2s)) {
            HiveDatasource hiveDatasource = new HiveDatasource();
            Map<String, String> hdfsVars = getClusterVarsMap(clusterId, "HDFS");
            Map<String, String> hiveVars = getClusterVarsMap(clusterId, "HIVE");

            hiveDatasource.setHost(hiveServer2s.get(0).getHostname());
            hiveDatasource.setPort("10000");
            hiveDatasource.setAuthentication("SIMPLE");
            hiveDatasource.setUserName("hive");
            hiveDatasource.setPassword("hive");
            String dfsNameservices = hdfsVars.getOrDefault("${dfs.nameservices}", "");
            hiveDatasource.setDefaultFs(String.format("hdfs://%s", dfsNameservices));
            hiveDatasource.setThriftUrls(hiveVars.getOrDefault("${hive.metastore.uris}", ""));
            hiveDatasource.setWarehouse(hiveVars.getOrDefault("${hive.metastore.warehouse.dir}", ""));
            String haNodes = hdfsVars.getOrDefault("${dfs.ha.namenodes.${dfs.nameservices}}", "");
            hiveDatasource.setHadoopConfig(getHadoopConfig(hdfsVars, haNodes, dfsNameservices));
            logger.info("hive get info success");
            return hiveDatasource;
        } else {
            logger.warn("HiveServer2 instances cannot be found");
        }
        return null;
    }

    public PaimonDatasource getPaimonDatasource(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> sparkThriftServers = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "SparkThriftServer");

        if (CollectionUtils.isNotEmpty(sparkThriftServers)) {
            PaimonDatasource paimonDatasource = new PaimonDatasource();
            Map<String, String> hdfsVars = getClusterVarsMap(clusterId, "HDFS");
            Map<String, String> hiveVars = getClusterVarsMap(clusterId, "HIVE");

            paimonDatasource.setHost(sparkThriftServers.get(0).getHostname());
            paimonDatasource.setPort("10016");
            paimonDatasource.setUserName("hive");
            paimonDatasource.setPassword("hive");
            paimonDatasource.setStorageType("HDFS");

            String dfsNameservices = hdfsVars.getOrDefault("${dfs.nameservices}", "");
            paimonDatasource.setDefaultFs(String.format("hdfs://%s", dfsNameservices));
            paimonDatasource.setThriftUrls(hiveVars.getOrDefault("${hive.metastore.uris}", ""));
            paimonDatasource.setWarehouse(hiveVars.getOrDefault("${hive.metastore.warehouse.dir}", ""));
            String haNodes = hdfsVars.getOrDefault("${dfs.ha.namenodes.${dfs.nameservices}}", "");
            paimonDatasource.setHadoopConfig(getHadoopConfig(hdfsVars, haNodes, dfsNameservices));
            logger.info("paimon get info success");
            return paimonDatasource;
        } else {
            logger.warn("paimon SparkThriftServers instances cannot be found");
        }
        return null;
    }

    private String getHadoopConfig(Map<String, String> hdfsVars, String haNodes, String dfsNameservices) {
        if (StringUtils.isBlank(haNodes)) {
            return "";
        }
        String nn1 = haNodes.split(Constants.COMMA)[0];
        String nn2 = haNodes.split(Constants.COMMA)[1];
        JSONObject hadoopConfigJson = new JSONObject();
        hadoopConfigJson.put("dfs.nameservices", dfsNameservices);
        hadoopConfigJson.put(String.format("dfs.ha.namenodes.%s", dfsNameservices), haNodes);
        hadoopConfigJson.put(String.format("dfs.namenode.rpc-address.%s.%s", dfsNameservices, nn1), hdfsVars.getOrDefault(String.format("${dfs.namenode.rpc-address.${dfs.nameservices}.%s}", nn1), ""));
        hadoopConfigJson.put(String.format("dfs.namenode.rpc-address.%s.%s", dfsNameservices, nn2), hdfsVars.getOrDefault(String.format("${dfs.namenode.rpc-address.${dfs.nameservices}.%s}", nn2), ""));
        hadoopConfigJson.put(String.format("dfs.client.failover.proxy.provider.%s", dfsNameservices), hdfsVars.getOrDefault("${dfs.client.failover.proxy.provider.${dfs.nameservices}}", ""));
        return hadoopConfigJson.toJSONString();

    }

    public DorisDatasource getDorisDatasource(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> dorisFEs = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "DorisFE");
        List<ClusterServiceRoleInstanceEntity> dorisBEs = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "DorisBE");

        if (CollectionUtils.isNotEmpty(dorisFEs) && CollectionUtils.isNotEmpty(dorisBEs)) {
            Map<String, String> dorisVars = getClusterVarsMap(clusterId, "DORIS");
            DorisDatasource dorisDatasource = new DorisDatasource();
            dorisDatasource.setHost(dorisFEs.get(0).getHostname());
            dorisDatasource.setPort(dorisVars.getOrDefault("${query_port}", "9030"));
            dorisDatasource.setWebPort(dorisVars.getOrDefault("${http_port}", "8030"));
            dorisDatasource.setUserName("root");
            dorisDatasource.setPassword(dorisVars.getOrDefault("${root_password}", ""));

            String webserverPort = dorisVars.getOrDefault("${webserver_port}", "8040");
            List<String> beHostPorts = dorisBEs.stream().map(x -> String.format("%s:%s", x.getHostname(), webserverPort)).collect(Collectors.toList());
            dorisDatasource.setBeHostPorts(beHostPorts);
            dorisDatasource.setBeHostPorts(beHostPorts);
            logger.info("doris get info success");

            return dorisDatasource;
        } else {
            logger.warn("DorisFE or DorisBE instances cannot be found");
        }
        return null;
    }

    public KafkaDatasource getKafkaDatasource(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> kafkas = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(
                clusterId, "KafkaBroker");
        if (CollectionUtils.isNotEmpty(kafkas)) {
            KafkaDatasource kafkaDatasource = new KafkaDatasource();
            String bootstrapServers = kafkas.stream().map(x -> String.format("%s:%s", x.getHostname(), "9092")).collect(Collectors.joining(Constants.COMMA));
            kafkaDatasource.setBootstrapServers(bootstrapServers);
            kafkaDatasource.setUserName("");
            kafkaDatasource.setPassword("");
            kafkaDatasource.setSecurityProtocol("");
            logger.info("kafka get info success");

            return kafkaDatasource;
        } else {
            logger.warn("KafkaBroker instances cannot be found");
        }
        return null;
    }

    public Map<String, String> getClusterVarsMap(Integer clusterId, String serviceName) {
        Map<String, String> varMaps = new HashMap<>();
        varMaps = clusterVariableService.getVariables(clusterId, serviceName)
                .stream().collect(Collectors.toMap(ClusterVariable::getVariableName, ClusterVariable::getVariableValue));
        return varMaps;
    }

}
