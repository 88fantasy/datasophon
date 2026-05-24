package com.datasophon.cli.s3;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.model.Host;
import com.datasophon.common.utils.ExecResult;

import java.io.File;

public interface S3 {
    
    String type();
    
    void setConfig(Object config);
    
    ExecResult install(File file, Executor executor, Host host);
    
    ExecResult start(Executor executor, Host host);
    
    ExecResult stop(Executor executor, Host host);
    
    ExecResult status(Executor executor, Host host);
    
}
