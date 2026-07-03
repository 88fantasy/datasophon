/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.datasophon.api.load.LoadServiceMeta;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
// gRPC server 端口固定为 18081（不随 RANDOM_PORT 一起变化），必须在类结束后关闭 Context 释放端口，
// 否则与 DataSophonApplicationServerTest 各自的 Context 会争抢同一端口导致 BindException
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
            try (
                    Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        });
    }
}
