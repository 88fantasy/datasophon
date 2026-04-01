package com.datasophon.worker.hook.nacos;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.utils.NamingHttpUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * @author zhanghuangbin
 */
public class InitNacosHookAction implements HookAction {


    @Override
    public String getType() {
        return "nacosSync";
    }

    @Override
    public ExecResult invoke(HookContext context) {
        Logger logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), InitNacosHookAction.class));
        InitNacosParams params = createParams(context);
        ConfigService configService = null;
        try {
            createNamespaceIfAbsent(params, logger);
            configService = createConfigService(params);
            if (params.getResources() == null || params.getResources().isEmpty()) {
                logger.warn("resource list is empty, {} migrate nothing to nacos: {}", context.getServiceName(), params.getServerAddr());
            } else {
                for (InitNacosParams.Resource resource : params.getResources()) {
                    publishConfig(configService, resource, params.getGroup(), logger);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ExecResult.error(String.format("服务%s更新 nacos 配置失败，%s", context.getServiceName(), e.getMessage()));
        } finally {
            if (configService != null) {
                try {
                    configService.shutDown();
                } catch (NacosException ignore) {
                }
            }
        }

        return ExecResult.success(String.format("服务%s更新 nacos 配置成功", context.getServiceName()));
    }

    private InitNacosParams createParams(HookContext context) {
        InitNacosParams params = context.getParamsAs(InitNacosParams.class);

        params.setServerAddr(PlaceholderUtils.replacePlaceholders(params.getServerAddr(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));
        params.setGroup(PlaceholderUtils.replacePlaceholders(params.getGroup(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));
        params.setUsername(PlaceholderUtils.replacePlaceholders(params.getUsername(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));
        params.setPassword(PlaceholderUtils.replacePlaceholders(params.getPassword(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));
        params.setNamespace(PlaceholderUtils.replacePlaceholders(params.getNamespace(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));

        List<InitNacosParams.Resource> resources = params.getResources();
        if (resources != null) {
            for (InitNacosParams.Resource resource : resources) {
                String path = PlaceholderUtils.replacePlaceholders(resource.getPath(), context.getGlobalVariables(), Constants.REGEX_VARIABLE);
                if (!path.startsWith("/")) {
//                    path = context.getPath() + "/" + path;
                }
                resource.setPath(path);
            }
        }

        return params;
    }

    private ConfigService createConfigService(InitNacosParams params) throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, params.getServerAddr());

        if (StrUtil.isNotBlank(params.getUsername())) {
            properties.setProperty("username", params.getUsername());
            properties.setProperty("password", params.getPassword());
        }
        properties.put(PropertyKeyConst.NAMESPACE, params.getNamespace());
        return NacosFactory.createConfigService(properties);
    }

    private void createNamespaceIfAbsent(InitNacosParams properties, Logger logger) throws Exception {
        String namespacesUrl = "/nacos/v1/console/namespaces";
        String url = "http://" + properties.getServerAddr();
        String namespace = properties.getNamespace();
        Header header = NamingHttpUtil.builderHeader();

        NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();
        HttpRestResult<String> result = nacosRestTemplate.get(url + namespacesUrl, header, Query.newInstance().initParams(new HashMap<>()), String.class);

        boolean success = false;
        if (result.getCode() == 200) {
            String data = result.getData();
            JSONArray jsonArray = JSONUtil.parseObj(data).getJSONArray("data");
            success = jsonArray.stream().anyMatch(item -> Objects.equals(JSONUtil.parseObj(item).getStr("namespace"), namespace));
            if (!success) {
                logger.info("create namespace: {}", namespace);
                Map<String, String> params = new HashMap<>();
                params.put("customNamespaceId", namespace);
                params.put("namespaceName", namespace);
                params.put("namespaceDesc", namespace);
                if (StrUtil.isNotBlank(properties.getUsername())) {
                    params.put(PropertyKeyConst.USERNAME, properties.getUsername());
                    params.put(PropertyKeyConst.PASSWORD, properties.getPassword());
                }

                HttpRestResult<Object> postForm = nacosRestTemplate.postForm(url + namespacesUrl, header, params, String.class);
                success = postForm.getCode() == 200;
            }
        }
        if (!success) {
            throw new IllegalStateException(String.format("check namespace %s failed", namespace));
        }
    }

    private void publishConfig(ConfigService configService, InitNacosParams.Resource resource, String group, Logger logger) {
        File resourceFile = new File(resource.getPath());
        if (!resourceFile.exists()) {
            logger.warn("resource resourceFile not found: {}", resource.getPath());
            return;
        }

        if (resourceFile.isFile()) {
            String dataId = StrUtil.isNotBlank(resource.getConfigName()) ? resource.getConfigName() : resourceFile.getName();
            publishSingleFile(configService, resourceFile, dataId, group, resource.isOverwrite(), logger);
        } else if (resourceFile.isDirectory()) {
            List<File> files = FileUtil.loopFiles(resourceFile);
            for (File file : files) {
                if (file.isFile()) {
                    String dataId = file.getName();
                    publishSingleFile(configService, file, dataId, group, resource.isOverwrite(), logger);
                }
            }
        }
    }

    private void publishSingleFile(ConfigService configService, File file, String dataId, String group, boolean overwrite, Logger logger) {
        String content = FileUtil.readUtf8String(file);
        try {
            if (!overwrite) {
                String original = configService.getConfig(dataId, group, 3000);
                if (original != null) {
                    logger.info("dataId: {}, group: {} exists, ignore it", dataId, group);
                    return;
                }
            }
            String suffix = FileUtil.getSuffix(dataId);
            suffix = suffix == null ? null : suffix.toLowerCase();
            if (ConfigType.isValidType(suffix) || "yml".equalsIgnoreCase(suffix)) {
                suffix = "yml".equalsIgnoreCase(suffix) ? "yaml" : suffix;
                configService.publishConfig(dataId, group, content, suffix);
            } else {
                configService.publishConfig(dataId, group, content);
            }
            logger.info("publish config to nacos successfully, dataId: {}, group: {}", dataId, group);
        } catch (NacosException e) {
            throw new IllegalStateException(String.format("publish config to nacos failed, dataId: %s, group: %s", dataId, group), e);
        }
    }

}
