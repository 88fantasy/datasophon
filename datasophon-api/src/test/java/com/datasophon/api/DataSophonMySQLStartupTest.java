/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.api;

import com.datasophon.api.load.LoadServiceMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * MySQL 集成启动测试（Integration Smoke Test）
 *
 * <p>验证目标：
 * <ol>
 *   <li>Spring 上下文使用真实 MySQL 数据源能够正常加载</li>
 *   <li>Druid 连接池配置正确，可与 MySQL 建立连接</li>
 *   <li>Flyway 数据库迁移执行成功</li>
 *   <li>内嵌 Web 服务器在随机端口成功启动</li>
 * </ol>
 *
 * <p>运行前提：
 * <ul>
 *   <li>本地 MySQL 127.0.0.1:3306 已启动</li>
 *   <li>数据库 {@code datasophon} 已创建</li>
 *   <li>账号 {@code root / localmysql} 具备 DDL 权限（供 Flyway 迁移使用）</li>
 * </ul>
 *
 * <p>隔离策略：
 * <ul>
 *   <li>激活 {@code integration} profile → 加载 application-integration.yml（真实 MySQL）</li>
 *   <li>{@link LoadServiceMeta} → {@code @MockBean} 拦截，跳过启动时的服务元数据查询</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
class DataSophonMySQLStartupTest {

    @Autowired
    private ApplicationContext context;

    /**
     * 拦截 LoadServiceMeta，避免启动时对服务元数据表发起复杂查询
     * （数据库可能尚无业务数据，仅验证结构迁移与上下文加载）
     */
    @MockitoBean
    private LoadServiceMeta loadServiceMeta;

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    /**
     * 核心启动测试：验证使用真实 MySQL 时 Spring 上下文可完整加载
     */
    @Test
    @DisplayName("Spring 上下文（MySQL）正常加载")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    /**
     * 验证内嵌 Web 服务器已绑定随机端口
     */
    @Test
    @DisplayName("Web 服务器在随机端口启动")
    void webServerStartedOnRandomPort() {
        assertThat(port)
                .as("服务器端口应为正整数（OS 随机分配）")
                .isPositive();
    }

    /**
     * 验证 Druid DataSource Bean 已注册
     */
    @Test
    @DisplayName("Druid DataSource Bean 完成注册")
    void dataSourceBeanExists() {
        assertThat(dataSource)
                .as("DataSource Bean 应存在且不为 null")
                .isNotNull();
        assertThat(dataSource.getClass().getName())
                .as("应使用 Druid 连接池")
                .contains("DruidDataSource");
    }

    /**
     * 验证 Druid 能与真实 MySQL 建立连接并执行查询
     */
    @Test
    @DisplayName("可成功连接 MySQL 并执行查询")
    void canConnectToMysql() {
        assertThatNoException().isThrownBy(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        });
    }
}
