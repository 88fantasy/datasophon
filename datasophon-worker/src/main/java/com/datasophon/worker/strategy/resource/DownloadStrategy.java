package com.datasophon.worker.strategy.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PropertyUtils;

import java.io.File;

import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.hutool.core.map.MapUtil;
import cn.hutool.http.HttpUtil;

@EqualsAndHashCode(callSuper = true)
@Data
public class DownloadStrategy extends ResourceStrategy {
    
    public static final String DOWNLOAD_TYPE = "download";
    
    private String from;
    
    private String to;
    
    private String md5;

    @Override
    public String type() {
        return DOWNLOAD_TYPE;
    }

    @Override
    public ExecResult exec() {
        logger.info("开始执行资源策略:{}...", DOWNLOAD_TYPE);
        File file = new File(basePath + Constants.SLASH + to);
        if (file.exists() && FileUtils.md5(file).equals(md5)) {
            logger.info("资源 {} 已经存在, 无需下载", to);
            return ExecResult.success();
        }
        
        logger.info("start to download resource : {}", from);
        
        String masterHost = PropertyUtils.getString(Constants.MASTER_HOST);
        String masterPort = PropertyUtils.getString(Constants.MASTER_WEB_PORT);
        String params = HttpUtil.toParams(MapUtil.<String, Object>builder("frameCode", frameCode)
                .put("serviceRoleName", serviceRole)
                .put("resource", from)
                .build());
        
        String url = "http://" + masterHost + ":" + masterPort + "/ddh/api/service/install/downloadResource?" + params;
//        超时时间为30,约定资源不应该很大
        HttpUtil.downloadFile(url, file, 30 * 1000);
        if(file.exists()){
            ShellUtils.execShell(String.format("chmod 755 %s", basePath + Constants.SLASH + to));
        }
        
        logger.info("end to download resource {} to {} ", from, to);
        return ExecResult.success();
    }
}
