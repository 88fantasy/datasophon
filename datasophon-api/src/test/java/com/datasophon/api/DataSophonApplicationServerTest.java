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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务启动冒烟测试（Smoke Test）
 *
 * <p>验证目标：
 * <ol>
 *   <li>Spring 上下文能够正常加载，所有 Bean 无循环依赖</li>
 *   <li>内嵌 Web 服务器在随机端口成功启动</li>
 *   <li>DataSource（H2 内存库）Bean 已完成注册</li>
 * </ol>
 *
 * <p>测试隔离策略：
 * <ul>
 *   <li>DataSource → H2 内存库（MySQL 兼容模式），避免依赖外部 MySQL</li>
 *   <li>Pekko → 使用 src/test/resources/application.conf，端口 0（随机分配）</li>
 *   <li>{@link LoadServiceMeta} → {@code @MockBean} 拦截，跳过启动时 DB 查询</li>
 *   <li>DB 迁移 → {@code datasophon.migration.enable=false}（application-test.yml）</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DataSophonApplicationServerTest {

    /**
     * 注入完整的 Spring ApplicationContext，用于断言 Bean 注册情况
     */
    @Autowired
    private ApplicationContext context;

    /**
     * 拦截 LoadServiceMeta（ApplicationRunner）的 run()，
     * 避免启动时对空 H2 库发起真实查询
     */
    @MockitoBean
    private LoadServiceMeta loadServiceMeta;

    /**
     * Spring Boot 测试框架注入的随机端口号
     */
    @LocalServerPort
    private int port;

    /**
     * 核心启动测试：验证 Spring 上下文完整加载
     *
     * <p>若上下文加载失败（Bean 创建异常、循环依赖、配置缺失等），
     * 该测试会在上下文初始化阶段直接报错，不会走到断言。
     */
    @Test
    @DisplayName("Spring 上下文正常加载")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    /**
     * 验证内嵌 Web 服务器已在随机端口绑定并启动
     */
    @Test
    @DisplayName("Web 服务器在随机端口启动")
    void webServerStartedOnRandomPort() {
        assertThat(port)
                .as("服务器端口应为正整数（OS 随机分配）")
                .isPositive();
    }

    /**
     * 验证 DataSource Bean 已注册——即数据源配置（H2）正确解析
     */
    @Test
    @DisplayName("DataSource Bean 完成注册")
    void dataSourceBeanExists() {
        DataSource dataSource = context.getBean(DataSource.class);
        assertThat(dataSource)
                .as("DataSource Bean 应存在且不为 null")
                .isNotNull();
    }
}
