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

package com.datasophon.api.observability;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OtelSchemaOrchestrator {

    private final OtelExporterSwitchService switchService;
    private final DorisDataSourceFactory dataSourceFactory;
    private final OtelCredentialService credentialService;
    private final OtelSchemaRunner schemaRunner;

    public OtelSchemaOrchestrator(OtelExporterSwitchService switchService,
                                  DorisDataSourceFactory dataSourceFactory,
                                  OtelCredentialService credentialService,
                                  OtelSchemaRunner schemaRunner) {
        this.switchService = switchService;
        this.dataSourceFactory = dataSourceFactory;
        this.credentialService = credentialService;
        this.schemaRunner = schemaRunner;
    }

    public void applyIfReady(Integer clusterId) {
        if (!switchService.isDorisReady(clusterId)) {
            log.info("cluster {} Doris 未就绪(FE/BE 角色状态未全部 RUNNING)，跳过 otel schema 初始化", clusterId);
            return;
        }
        OtelCredentials credentials = credentialService.getOrCreate(clusterId);
        schemaRunner.apply(dataSourceFactory.create(clusterId), credentials);
        log.info("cluster {} otel schema 初始化完成", clusterId);
    }
}
