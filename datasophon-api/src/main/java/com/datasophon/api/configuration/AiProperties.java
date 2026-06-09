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

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "datasophon.ai")
public class AiProperties {
    
    private static final Logger log = LoggerFactory.getLogger(AiProperties.class);
    
    private String sidecarUrl = "http://localhost:18090";
    
    private String internalToken = "change-me";
    
    @PostConstruct
    public void validate() {
        if ("change-me".equals(internalToken)) {
            log.warn("datasophon.ai.internal-token is using the default insecure value. "
                    + "Set DDH_AI_INTERNAL_TOKEN environment variable in production.");
        }
    }
    
    public String getSidecarUrl() {
        return sidecarUrl;
    }
    
    public void setSidecarUrl(String sidecarUrl) {
        this.sidecarUrl = sidecarUrl;
    }
    
    public String getInternalToken() {
        return internalToken;
    }
    
    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}
