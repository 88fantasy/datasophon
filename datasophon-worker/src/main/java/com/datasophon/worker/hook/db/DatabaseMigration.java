package com.datasophon.worker.hook.db;

import cn.hutool.core.io.FileUtil;
import lombok.Setter;
import org.slf4j.Logger;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DatabaseMigration {

    private static final String MIGRATION_TABLE_NAME = "t_ddh_srv_db_migration_history";

    public static final String SPLIT = "__";

    private static final String QUERY_HISTORY_SQL = "select * from %s where resource_key = ? ";


    private final Connection metaConn;

    private final Connection execConn;


    @Setter
    private Logger logger;


    private final JdbcConnectorUtils.ResultSetHandler<Migration> resultSetHandler = rs -> {
        Migration migration = new Migration();
        migration.setSuccess(rs.getBoolean("SUCCESS"));
        migration.setVersion(rs.getString("VERSION"));
        migration.setExecuteDate(rs.getDate("EXECUTE_DATE"));
        migration.setExecuteUser(rs.getString("EXECUTE_USER"));
        migration.setResourceKey(rs.getString("RESOURCE_KEY"));
        return migration;
    };

    public DatabaseMigration(Connection metaConn, Connection execConn) {
        this.metaConn = metaConn;
        this.execConn = execConn;
    }

    public TreeSet<Migration> getMigrations(InitDbParams params) throws SQLException {
        TreeSet<Migration> migrations = getAllMigrations(params);
        if (migrations.isEmpty()) {
            return new TreeSet<>();
        }

        TreeSet<Migration> migrationHistories = new TreeSet<>(
                JdbcConnectorUtils.queryList(metaConn, String.format(QUERY_HISTORY_SQL, MIGRATION_TABLE_NAME), resultSetHandler, params.getResourceKey())
        );
        if (CollectionUtils.isEmpty(migrationHistories)) {
            return migrations;
        } else {
            Migration lastMigration = migrationHistories.last();
            return (TreeSet<Migration>) migrations.tailSet(lastMigration, !lastMigration.isSuccess());
        }
    }


    private TreeSet<Migration> getAllMigrations(InitDbParams params) {
        List<Resource> resources = new ArrayList<>();
        File home = new File(params.getScriptPath());
        if (home.exists() && home.isDirectory()) {
            List<File> sqlFiles = FileUtil.loopFiles(home, pathname -> "sql".equals(FileUtil.getSuffix(pathname.getName())));
            resources = sqlFiles.stream().map(FileSystemResource::new).filter(Migration::isMigrationFile).collect(Collectors.toList());
        }

        Pattern ddlPattern = Pattern.compile(params.getDdlPattern());
        Pattern dmlPattern = Pattern.compile(params.getDmlPattern());
        Pattern rollbackPattern = Pattern.compile(params.getRollbackPattern());

        Map<String, Migration> map = new HashMap<>();
        for (Resource resource : resources) {
            String fileName = resource.getFilename();
            Resource ddl = null, dml = null, rollback = null;
            String version = null;

            if ((version = getVersion(fileName, ddlPattern)) != null) {
                ddl = resource;
            } else if ((version = getVersion(fileName, dmlPattern)) != null) {
                dml = resource;
            } else if ((version = getVersion(fileName, rollbackPattern)) != null) {
                rollback = resource;
            }

            if (version == null) {
                continue;
            }
            Migration migration = map.computeIfAbsent(version, Migration::new);
            if (ddl != null) {
                migration.setUpgradeDDLFile(ddl);
            } else if (dml != null) {
                migration.setUpgradeDMLFile(dml);
            } else {
                migration.setRollbackFile(rollback);
            }
        }

        TreeSet<Migration> allMigrations = new TreeSet<>();
        map.values().forEach(migration-> {
            if (!allMigrations.add(migration)) {
                throw new RuntimeException(String.format("resourceKey %s, Duplicate version %s", migration.getResourceKey(), migration.getVersion()));
            }
        });

        return allMigrations;
    }


    private String getVersion(String fileName, Pattern pattern) {
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
           return matcher.group("version");
        }
        return null;
    }
    public Migration migrate(String resourceKey, TreeSet<Migration> migrations) throws SQLException {
        for (Migration migration : migrations) {
            logger.info("start migration, resourceKey: {}, version: {}", resourceKey, migration.getVersion());
            boolean success = doMigration(migration);
            migration.setExecuteUser("datasophon");
            migration.setExecuteDate(new Date());
            migration.setSuccess(success);
            upsertMigration(resourceKey, migration);
            if (!migration.isSuccess()) {
                logger.error("{} Migration break at version  {}", resourceKey, migration.getVersion());
                return migration;
            } else {
                logger.info("{} Migration success! version: {}", resourceKey, migration.getVersion());
            }
        }
        logger.info("The {} migration is complete , The latest database version is {}", resourceKey, migrations.last().getVersion());
        return null;
    }


    public boolean doMigration(Migration migration) {
        Resource ddlFile = migration.getUpgradeDDLFile(), dmlFile = migration.getUpgradeDMLFile();
        if (runScript(migration, ddlFile, true, false) && runScript(migration, dmlFile, true, true)) {
            return true;
        }
        logger.error("Migration failure! version: {}. A rollback is about to be performed", migration.getVersion());
        Resource rollbackFile = migration.getRollbackFile();
        if (rollbackFile != null) {
            runScript(migration, rollbackFile, false, false);
            logger.info("The rollback script ({}) is successfully executed", rollbackFile.getFilename());
        } else {
            logger.warn("The rollback script does not exist. Skip execution");
        }
        return false;
    }


    private void upsertMigration(String resourceKey, Migration migration) throws SQLException {
        Migration db = JdbcConnectorUtils.query(metaConn,
                String.format("select * from %s where resource_key = ? and version = ? ", MIGRATION_TABLE_NAME),
                resultSetHandler, resourceKey, migration.getVersion());
        if (db != null && !db.isSuccess()) {
            JdbcConnectorUtils.executeUpdate(metaConn,
                    String.format("update %s set success = ?, execute_date = ? where  resource_key = ? and version = ?", MIGRATION_TABLE_NAME),
                    migration.isSuccess() ? 1 : 0, new Date(),
                    resourceKey, migration.getVersion()
            );
        } else {
            JdbcConnectorUtils.executeUpdate(metaConn,
                    String.format("insert into %s(resource_key, version, execute_user, execute_date, success) values(?, ?, ?, ?, ?)", MIGRATION_TABLE_NAME),
                    resourceKey, migration.getVersion(), "datasophon", new Date(), migration.isSuccess() ? 1 : 0
            );
        }

    }

    private boolean runScript(Migration migration, Resource resource, boolean stopOnError, boolean rollbackIfErr) {
        if (resource == null || !resource.exists()) {
            return true;
        }
        Boolean autoCommit = null;
        try {
            logger.info("exec sql resource: resourceKey: {}, resourceVersion: {}, fileName: {}", migration.getResourceKey(), migration.getVersion(), resource.getFilename());
            if (execConn.isClosed()) {
                logger.warn("exec sql resource: resourceKey: {}, but connection closed", migration.getResourceKey());
                return false;
            }
            autoCommit = execConn.getAutoCommit();
            ScriptRunner scriptRunner = new ScriptRunner(execConn);
            scriptRunner.setAutoCommit(false);
            scriptRunner.setStopOnError(stopOnError);
            scriptRunner.setSendFullScript(false);

            LogWriter logWriter = new LogWriter(System.out, logger);
            if (!stopOnError) {
                scriptRunner.setErrorLogWriter(logWriter);
            }
            scriptRunner.setLogWriter(logWriter);
            scriptRunner.runScript(new InputStreamReader(resource.getInputStream()));
            execConn.commit();
            return true;
        } catch (Exception e) {
            logger.error("Script execute failed! {}", resource.getFilename(), e);
            if (rollbackIfErr) {
                try {
                    execConn.rollback();
                } catch (SQLException ex) {
                    logger.warn("{} rollback fail", resource.getFilename());
                }
            }
            return false;
        } finally {
            try {
                if (!execConn.isClosed() && autoCommit != null) {
                    execConn.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
//                ignore
            }
        }
    }


    static class LogWriter extends PrintWriter {

        private final Logger logger;

        public LogWriter(OutputStream out, Logger logger) {
            super(out);
            this.logger = logger;
        }

        @Override
        public void write(String s) {
            logger.debug(s);
        }
    }

}
