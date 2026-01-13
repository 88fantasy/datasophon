package com.datasophon.worker.hook.s3;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.List;
import java.util.Map;

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
    public ExecResult invoke(HookContext context) throws Exception {
        S3SyncParams params = createSyncParams(context);
        S3Client client = null;
        try {
            client = createS3Client(params);

            S3SyncService service = new S3SyncService(params, client);
            List<ZipFileInfo> zipFiles = service.getUnsyncedVersion(params.getResourcePath(), params.getMetaObjectName());
            if (zipFiles.isEmpty()) {
                String endpoint = PlaceholderUtils.replacePlaceholders(params.getEndpoint(), context.getGlobalVariables(), Constants.REGEX_VARIABLE);
                log.info("{} migrate nothing to s3: {}/{}", context.getServiceName(), endpoint, params.getBucket());
            } else {
                service.sync(zipFiles, params.getMetaObjectName());
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ExecResult.error(String.format("服务%s更新oss失败，%s", context.getServiceName(), e.getMessage()));
        }  finally {
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
                                AwsBasicCredentials.create(params.getAccessKey(), params.getSecretKey())
                        )
                )
                .region(Region.US_EAST_1)
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }


    private String getResourcePath(String resourcePath, HookContext context) {
        if (StrUtil.isBlank(resourcePath)) {
            resourcePath = context.getPath() + "/oss/migration";
        } else {
            resourcePath = PlaceholderUtils.replacePlaceholders(resourcePath, context.getGlobalVariables(), Constants.REGEX_VARIABLE);
//            if (!resourcePath.startsWith("/")) {
//                resourcePath = context.getPath() + "/" + resourcePath;
//            }
        }
        return resourcePath;
    }



}
