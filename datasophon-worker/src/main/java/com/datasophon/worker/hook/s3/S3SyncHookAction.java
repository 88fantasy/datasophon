package com.datasophon.worker.hook.s3;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class S3SyncHookAction implements HookAction {
    
    @Override
    public String getType() {
        return "s3Sync";
    }
    
    @Override
    public ExecResult invoke(HookContext context) {
        Logger logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), S3SyncHookAction.class));
        
        S3SyncParams params = createSyncParams(context);
        S3Client client = null;
        try {
            client = createS3Client(params);
            
            S3SyncService service = new S3SyncService(params, client);
            service.createBucketIfAbsent(params.getBucket());
            
            if (params.getResourcePath() == null) {
                logger.info("resource path is empty, {} migrate nothing to s3: {}/{}", context.getServiceName(), params.getEndpoint(), params.getBucket());
            } else {
                List<ZipFileInfo> zipFiles = service.getUnsyncedVersion(params.getResourcePath(), params.getMetaObjectName());
                if (zipFiles.isEmpty()) {
                    logger.info("{} migrate nothing to s3: {}/{}", context.getServiceName(), params.getEndpoint(), params.getBucket());
                } else {
                    service.sync(zipFiles, params.getMetaObjectName());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ExecResult.error(String.format("服务%s更新oss失败，%s", context.getServiceName(), e.getMessage()));
        } finally {
            IoUtil.close(client);
        }
        
        return ExecResult.success(String.format("服务%s更新oss成功", context.getServiceName()));
    }
    
    private S3SyncParams createSyncParams(HookContext context) {
        S3SyncParams params = context.getParamsAs(S3SyncParams.class);
        
        Map<String, String> globalVariables = context.getGlobalVariables();
        params.setAccessKey(PlaceholderUtils.replacePlaceholders(params.getAccessKey(), globalVariables, Constants.REGEX_VARIABLE));
        params.setSecretKey(PlaceholderUtils.replacePlaceholders(params.getSecretKey(), globalVariables, Constants.REGEX_VARIABLE));
        params.setEndpoint(PlaceholderUtils.replacePlaceholders(params.getEndpoint(), globalVariables, Constants.REGEX_VARIABLE));
        params.setBucket(PlaceholderUtils.replacePlaceholders(params.getBucket(), globalVariables, Constants.REGEX_VARIABLE));
        params.setResourcePath(getResourcePath(params.getResourcePath(), context));
        
        if (StrUtil.isBlank(params.getMetaObjectName())) {
            params.setMetaObjectName("_datasophon_meta.json");
        }
        
        return params;
    }
    
    private S3Client createS3Client(S3SyncParams params) {
        return S3Client.builder()
                .endpointOverride(URI.create(params.getEndpoint()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(params.getAccessKey(), params.getSecretKey())))
                .region(Region.US_EAST_1)
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                .build();
    }
    
    private String getResourcePath(String resourcePath, HookContext context) {
        if (StrUtil.isBlank(resourcePath)) {
            resourcePath = null;
        } else {
            resourcePath = PlaceholderUtils.replacePlaceholders(resourcePath, context.getGlobalVariables(), Constants.REGEX_VARIABLE);
            if (!resourcePath.startsWith("/")) {
                resourcePath = context.getPath() + "/" + resourcePath;
            }
        }
        return resourcePath;
    }
    
}
