package com.datasophon.worker.strategy.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ShellUtils;

import java.io.File;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.io.FileUtil;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
public class LinkStrategy extends ResourceStrategy {
    
    public static final String LINK_TYPE = "link";
    
    private String source;
    
    private String target;
    
    @Override
    public void exec() {
        String realTarget = basePath + Constants.SLASH + target;
        File sourceFile = new File(source);
        File targetFile = new File(realTarget);
        log.info("link. sourceFile[{}] exist is {},targetFile[{}] exist is {}", source, sourceFile.exists(), realTarget, targetFile.exists());
        if(!FileUtil.exist(targetFile.getParent())){
            FileUtil.mkdir(targetFile.getParent());
        }
        if (!targetFile.exists() && sourceFile.exists()) {
            ShellUtils.execShell("ln -s " + source + " " + realTarget);
            log.info("Create symbolic dir: {} to {}", source, realTarget);
        }
    }
}
