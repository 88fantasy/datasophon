package com.datasophon.worker.hook.db;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Map;
import java.util.TreeSet;

/**
 * @author zhanghuangbin
 */
public class InitDbHook implements HookAction {


    private static final Logger log = LoggerFactory.getLogger(InitDbHook.class);

    @Override
    public ExecResult invoke(HookContext context) {
        Connection metaConn = null;
        Connection execConn = null;
        try {
            metaConn = getMetaConnection(context);
            execConn = getExecConnection(context);
            DatabaseMigration migration = new DatabaseMigration(metaConn, execConn);

            InitDbParams params = context.getParamsAs(InitDbParams.class);
            String resourceKey = PlaceholderUtils.replacePlaceholders(params.getResourceKey(), context.getGlobalVariables(), Constants.REGEX_VARIABLE);

            String scriptPath = params.getScriptPath();
            if (StrUtil.isBlank(scriptPath)) {
                scriptPath = context.getPath() + "/db/migration";
            } else {
                scriptPath = PlaceholderUtils.replacePlaceholders(scriptPath, context.getGlobalVariables(), Constants.REGEX_VARIABLE);
                if (!scriptPath.startsWith("/")) {
                    scriptPath = context.getPath() + "/" + scriptPath;
                }
            }
            TreeSet<Migration> migrations = migration.getMigrations(scriptPath, resourceKey);
            if (migrations.isEmpty()) {
                log.info("{} nothing to migration", resourceKey);
            } else {
                migration.migrate(resourceKey, migrations);
            }
            return ExecResult.success("初始化数据库成功");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ExecResult.error(String.format("更新%s数据库失败，%s", context.getServiceName(), e.getMessage()));
        } finally {
            IoUtil.close(metaConn);
            IoUtil.close(execConn);
        }
    }


    private Connection getMetaConnection(HookContext context) {
        Connection conn;
        if (context.getParams().get("metaStorage").equals("datasophon")) {
            conn = getDatasophoneConnection();
        } else {
            conn = getExecConnection(context);
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

    private Connection getExecConnection(HookContext context) {
        InitDbParams params = context.getParamsAs(InitDbParams.class);
        Map<String, String> globalVariables = context.getGlobalVariables();
        return JdbcConnectorUtils.getConnection(
                PlaceholderUtils.replacePlaceholders(params.getDriver(), globalVariables, Constants.REGEX_VARIABLE),
                PlaceholderUtils.replacePlaceholders(params.getUrl(), globalVariables, Constants.REGEX_VARIABLE),
                PlaceholderUtils.replacePlaceholders(params.getUsername(), globalVariables, Constants.REGEX_VARIABLE),
                PlaceholderUtils.replacePlaceholders(params.getPassword(), globalVariables, Constants.REGEX_VARIABLE)
        );
    }


}
