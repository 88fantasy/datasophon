package com.datasophon.worker.hook.db;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.TreeSet;

/**
 * @author zhanghuangbin
 */
public class InitDbHookAction implements HookAction {

    public static final String DDL_PATTERN = "^V\\d+(?<version>(\\.\\d+)*)__DDL\\.sql$";
    public static final String DML_PATTERN = "^V\\d+(?<version>(\\.\\d+)*)__DML\\.sql$";
    public static final String ROLLBACK_PATTERN = "^R\\d+(?<version>(\\.\\d+)*)\\.sql$";


    @Override
    public String getType() {
        return "initDb";
    }


    @Override
    public ExecResult invoke(HookContext context) {
        Logger logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(),InitDbHookAction.class));
        Connection metaConn = null;
        Connection execConn = null;
        try {
            InitDbParams params = createParams(context);

            metaConn = getMetaConnection(params);
            execConn = getExecConnection(params);
            DatabaseMigration migration = new DatabaseMigration(metaConn, execConn);
            migration.setLogger(logger);

            String resourceKey = PlaceholderUtils.replacePlaceholders(params.getResourceKey(), context.getGlobalVariables(), Constants.REGEX_VARIABLE);

            TreeSet<Migration> migrations = migration.getMigrations(params);
            if (migrations.isEmpty()) {
                logger.info("{} nothing to migration", resourceKey);
            } else {
                Migration errorMi = migration.migrate(resourceKey, migrations);
                if (errorMi != null) {
                    return ExecResult.error(String.format("更新数据库失败，resourceKey:%s, 版本:%s失败", resourceKey, errorMi.getVersion()));
                }
            }
            return ExecResult.success("初始化数据库成功");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ExecResult.error(String.format("更新%s数据库失败，%s", context.getServiceName(), e.getMessage()));
        } finally {
            IoUtil.close(metaConn);
            IoUtil.close(execConn);
        }
    }



    private InitDbParams createParams(HookContext context) {
        InitDbParams params = context.getParamsAs(InitDbParams.class);
        params.setResourceKey(PlaceholderUtils.replacePlaceholders(params.getResourceKey(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));

        String scriptPath = params.getScriptPath();
        if (StrUtil.isBlank(scriptPath)) {
            scriptPath = context.getPath() + "/db/migration";
        } else {
            scriptPath = PlaceholderUtils.replacePlaceholders(scriptPath, context.getGlobalVariables(), Constants.REGEX_VARIABLE);
            if (!scriptPath.startsWith("/")) {
                scriptPath = context.getPath() + "/" + scriptPath;
            }
        }
        params.setScriptPath(scriptPath);

        params.setDriver(PlaceholderUtils.replacePlaceholders(params.getDriver(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));
        params.setUrl(PlaceholderUtils.replacePlaceholders(params.getUrl(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));
        params.setUsername(PlaceholderUtils.replacePlaceholders(params.getUsername(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));
        params.setPassword(PlaceholderUtils.replacePlaceholders(params.getPassword(), context.getGlobalVariables(), Constants.REGEX_VARIABLE));

        if (StrUtil.isBlank(params.getDdlPattern())) {
            params.setDdlPattern(DDL_PATTERN);
        }
        if (StrUtil.isBlank(params.getDmlPattern())) {
            params.setDdlPattern(DML_PATTERN);
        }
        if (StrUtil.isBlank(params.getRollbackPattern())) {
            params.setDdlPattern(ROLLBACK_PATTERN);
        }
        return params;
    }

    private Connection getMetaConnection(InitDbParams params) {
        Connection conn;
        if (MetaStorage.datasophon.equals(params.getMetaStorage())) {
            conn = getDatasophoneConnection();
        } else {
            conn = getExecConnection(params);
        }

        return conn;
    }


    private Connection getDatasophoneConnection() {
        return JdbcConnectorUtils.getConnection(
                "com.mysql.cj.jdbc.Driver",
                String.format("jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf-8",
                        PropertyUtils.getString(Constants.DB_IP),
                        PropertyUtils.getString(Constants.DB_PORT),
                        PropertyUtils.getString(Constants.DB_NAME)
                ),
                PropertyUtils.getString(Constants.DB_USERNAME),
                PropertyUtils.getString(Constants.DB_PASSWORD)
        );
    }

    private Connection getExecConnection(InitDbParams params) {
        return JdbcConnectorUtils.getConnection(
                params.getDriver(),
                params.getUrl(),
                params.getUsername(),
                params.getPassword()
        );
    }


}
