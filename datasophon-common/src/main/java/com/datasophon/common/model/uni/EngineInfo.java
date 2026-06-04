package com.datasophon.common.model.uni;

import java.io.Serializable;

import lombok.Data;

@Data
public class EngineInfo implements Serializable {
    
    private Integer clusterId;
    
    private String easyFlowService;
    
    private String uSchedulerAddress;
    
    private String uSchedulerUserName;
    
    private String uSchedulerUserPassword;
    
    private String engineType;
    
    private String realTimeSchedulerAddress;
    
    private String realTimeSchedulerUserName;
    
    private String realTimeSchedulerUserPassword;
    
    private MysqlDatasource mysqlDatasource;
    
    private HiveDatasource hiveDatasource;
    
    private DorisDatasource dorisDatasource;
    
    private PaimonDatasource paimonDatasource;
    
    private KafkaDatasource kafkaDatasource;
    
}
