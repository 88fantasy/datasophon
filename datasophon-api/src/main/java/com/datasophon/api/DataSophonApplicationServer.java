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

import cn.hutool.extra.spring.EnableSpringUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@ServletComponentScan
@ComponentScan("com.datasophon")
@MapperScan("com.datasophon.dao")
@EnableSpringUtil
@EnableScheduling
@EnableAsync
public class DataSophonApplicationServer extends SpringBootServletInitializer {
    
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DataSophonApplicationServer.class);
        app.setAllowBeanDefinitionOverriding(true);
        app.setAllowCircularReferences(true);
        app.run(args);
    }
    
    @PostConstruct
    public void run() throws UnknownHostException {
        String hostName = InetAddress.getLocalHost().getHostName();
        CacheUtils.put(Constants.HOSTNAME, hostName);
    }
}
