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


package com.datasophon.api.security;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    @Value("${security.authentication.type:PASSWORD}")
    private String type;
    
    private AutowireCapableBeanFactory beanFactory;
    private AuthenticationType authenticationType;
    
    @Autowired
    public SecurityConfig(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    
    private void setAuthenticationType(String type) {
        if (StringUtils.isBlank(type)) {
            logger.info("security.authentication.type configuration is empty, the default value 'PASSWORD'");
            this.authenticationType = AuthenticationType.PASSWORD;
            return;
        }
        
        this.authenticationType = AuthenticationType.valueOf(type);
    }
    
    @Bean(name = "authenticator")
    public Authenticator authenticator() {
        setAuthenticationType(type);
        Authenticator authenticator;
        switch (authenticationType) {
            case PASSWORD:
                authenticator = new PasswordAuthenticator();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + authenticationType);
        }
        beanFactory.autowireBean(authenticator);
        return authenticator;
    }
}
