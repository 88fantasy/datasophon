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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DatabaseMigration {

    private static final String MIGRATION_TABLE_NAME = "t_ddh_srv_db_migration_history";

    public static final String SPLIT = "__";

    private static final String TABLE_CREATE_SQL = String.format(
            "CREATE TABLE `%s`  (" +
            "  `resource_key` varchar(128) NOT NULL," +
            "  `version` varchar(128) NOT NULL," +
            "  `execute_user` varchar(128) NOT NULL," +
            "  `execute_date` timestamp NOT NULL," +
            "  `success` int(1) NOT NULL," +
            "  PRIMARY KEY (`resource_key`, `version`)" +
            ")",
            MIGRATION_TABLE_NAME
    );
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

    public TreeSet<Migration> getMigrations(String scriptPath, String resourceKey) throws SQLException {
        TreeSet<Migration> migrations = getAllMigrations(scriptPath);
        if (migrations.isEmpty()) {
            return new TreeSet<>();
        }

        prepareMigrationTable(metaConn);
        TreeSet<Migration> migrationHistories = new TreeSet<>(
                JdbcConnectorUtils.queryList(metaConn, String.format(QUERY_HISTORY_SQL, MIGRATION_TABLE_NAME), resultSetHandler, resourceKey)
        );
        if (CollectionUtils.isEmpty(migrationHistories)) {
            return migrations;
        } else {
            Migration lastMigration = migrationHistories.last();
            return (TreeSet<Migration>) migrations.tailSet(lastMigration, !lastMigration.isSuccess());
        }
    }

    private void prepareMigrationTable(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        boolean found;
        try (ResultSet rs = meta.getTables(connection.getCatalog(), connection.getSchema(), MIGRATION_TABLE_NAME, new String[]{"TABLE"})) {
            found = rs.next();
        }
        if (!found) {
            logger.info("create table {} at {}.{}", MIGRATION_TABLE_NAME, connection.getCatalog(), connection.getSchema());
            JdbcConnectorUtils.executeUpdate(connection, TABLE_CREATE_SQL);
        }
    }


    private TreeSet<Migration> getAllMigrations(String scriptPath) {
        TreeSet<Migration> allMigrations = new TreeSet<>();
        Resource[] resources = new Resource[0];

        File home = new File(scriptPath);
        if (home.exists() && home.isDirectory()) {
            List<File> sqlFiles = FileUtil.loopFiles(home, pathname -> "sql".equals(FileUtil.getSuffix(pathname.getName())));
            resources = sqlFiles.stream().map(FileSystemResource::new).toArray(Resource[]::new);
        }


        Map<String, List<Resource>> resourceMap = Arrays.stream(resources)
                .filter(Migration::isMigrationFile)
                .collect(Collectors.groupingBy(
                        r -> Objects.requireNonNull(r.getFilename()).substring(1, r.getFilename().indexOf(SPLIT))));

        resourceMap.forEach((version, files) -> {
            Resource ddl = null, dml = null, rollback = null;
            for (Resource resource : files) {
                String fileName = FileUtil.mainName(Objects.requireNonNull(resource.getFilename()));
                if (fileName.startsWith(ScriptType.UPGRADE.getPrefix())) {
                    String[] extracts = fileName.split(SPLIT);
                    if ("DDL".equals(extracts[1])) {
                        ddl = resource;
                    } else if ("DML".equals(extracts[1])) {
                        dml = resource;
                    }
                } else if (fileName.startsWith(ScriptType.ROLLBACK.getPrefix())) {
                    rollback = resource;
                }
            }
            if (ddl != null || dml != null) {
                Migration migration = new Migration(version, ddl, dml, rollback);
                if (!allMigrations.add(migration)) {
                    throw new RuntimeException(String.format("resourceKey %s, Duplicate version %s", migration.getResourceKey(), migration.getVersion()));
                }
            }
        });

        return allMigrations;
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
        try {
            logger.info("exec sql resource: resourceKey: {}, resourceVersion: {}, fileName: {}", migration.getResourceKey(), migration.getVersion(), resource.getFilename());
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
