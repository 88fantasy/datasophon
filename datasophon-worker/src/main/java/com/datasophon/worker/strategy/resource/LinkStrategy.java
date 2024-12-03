package com.datasophon.worker.strategy.resource;

import cn.hutool.core.io.FileUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@EqualsAndHashCode(callSuper = true)
@Data
public class LinkStrategy extends ResourceStrategy {
    
    public static final String LINK_TYPE = "link";
    
    private String source;
    
    private String target;

    @Override
    public String type() {
        return LINK_TYPE;
    }

    @Override
    public ExecResult exec() {
        String realTarget = basePath + Constants.SLASH + target;
        if(target.startsWith(Constants.SLASH)){
            // 兼容绝对路径
            realTarget = target;
        }
        File sourceFile = new File(source);
        File targetFile = new File(realTarget);
        logger.info("link. sourceFile[{}] exist is {},targetFile[{}] exist is {}", source, sourceFile.exists(), realTarget, targetFile.exists());
        if(!FileUtil.exist(targetFile.getParent())){
            FileUtil.mkdir(targetFile.getParent());
        }
        if (!targetFile.exists() && sourceFile.exists()) {
            ShellUtils.execShell("ln -s " + source + " " + realTarget);
            logger.info("Create symbolic dir: {} to {}", source, realTarget);
        }
        return ExecResult.success();
    }
}
