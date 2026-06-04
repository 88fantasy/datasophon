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

package com.datasophon.api.configuration;

import com.datasophon.api.migration.DatabaseMigration;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DatabaseMigrationAware implements ApplicationContextAware, Ordered {
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        String migrationEnable = applicationContext.getEnvironment().getProperty("datasophon.migration.enable");
        log.info("Database Migration enable is {}", migrationEnable);
        if (migrationEnable == null || "false".equals(migrationEnable)) {
            return;
        }
        DatabaseMigration databaseMigration = applicationContext.getBean(DatabaseMigration.class);
        try {
            databaseMigration.migration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
    
}
