package com.datasophon.worker.strategy.resource;

import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import org.slf4j.Logger;

import java.util.Map;

@Data
public abstract class ResourceStrategy {
    
    public static final String TYPE_KEY = "type";

    Logger logger;

    String frameCode;
    
    String service;
    
    String serviceRole;
    
    String basePath;

    Map<String, String> variables;

    public abstract String type();

    public abstract ExecResult exec();

    
}
