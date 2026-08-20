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

package com.datasophon.api.vo.k8s;

import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.K8sAuthType;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

/** 不包含 kubeconfig、token 和密码等敏感字段的 K8s 连接配置响应。 */
@Data
public class K8sClusterConfigVO {

    private Integer id;
    private Integer clusterId;
    private K8sAuthType type;
    private String serverHost;
    private String serverCert;
    private String username;
    private String dorisHost;
    private Integer dorisPort;
    private String dorisDatabase;
    private boolean credentialConfigured;

    public static K8sClusterConfigVO from(K8sClusterConfig config) {
        if (config == null) {
            return null;
        }
        K8sClusterConfigVO vo = new K8sClusterConfigVO();
        vo.setId(config.getId());
        vo.setClusterId(config.getClusterId());
        vo.setType(config.getType());
        vo.setServerHost(config.getServerHost());
        vo.setServerCert(config.getServerCert());
        vo.setUsername(config.getUsername());
        vo.setDorisHost(config.getDorisHost());
        vo.setDorisPort(config.getDorisPort());
        vo.setDorisDatabase(config.getDorisDatabase());
        vo.setCredentialConfigured(hasCredential(config));
        return vo;
    }

    private static boolean hasCredential(K8sClusterConfig config) {
        if (config.getType() == null) {
            return false;
        }
        return switch (config.getType()) {
            case config_file -> StringUtils.isNotBlank(config.getKubeConfig());
            case token -> StringUtils.isNotBlank(config.getToken());
            case password -> StringUtils.isNotBlank(config.getPassword());
        };
    }
}
