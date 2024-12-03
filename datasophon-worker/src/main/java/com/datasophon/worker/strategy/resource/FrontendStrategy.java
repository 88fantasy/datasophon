package com.datasophon.worker.strategy.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.utils.JuicefsUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
public class FrontendStrategy extends ResourceStrategy {

    public static final String FRONTEND_TYPE = "frontend";

    private String meta;

    private String source;

    private String target;

    @Override
    public String type() {
        return FRONTEND_TYPE;
    }

    @Override
    public ExecResult exec() {
        ExecResult execResult = new ExecResult();
        if (StringUtils.isNotEmpty(meta)) {
            String metaUrl = variables.get(meta);
            if (StringUtils.isEmpty(metaUrl)) {
                execResult.setExecErrOut("缺少juicefs元数据地址");
                return execResult;
            }
            try {
                JuicefsUtil.installFrontend(logger, metaUrl, basePath + Constants.SLASH + source, target);
                execResult.setExecResult(true);
            } catch (IOException e) {
                logger.error("上传失败: " + e.getMessage(), e);
                execResult.setExecErrOut(e.getMessage());
            }
        } else {
            execResult.setExecErrOut("未配置juicefs元数据变量");
        }
        return execResult;
    }
}
