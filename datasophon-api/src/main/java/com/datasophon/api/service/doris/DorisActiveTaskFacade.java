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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.service.doris;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;

import com.datasophon.api.doris.DorisAdminReaderFactory;
import com.datasophon.api.dto.v2.DorisActiveTaskQueryDTO;
import com.datasophon.api.dto.v2.DorisActiveTaskResponseVO;
import com.datasophon.api.dto.v2.DorisActiveTaskVO;
import com.datasophon.api.enums.Status;
import com.datasophon.api.security.SystemAdminGuard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Orchestrates authorization, instance validation, connection, and active-task querying. */
@Service
public class DorisActiveTaskFacade {

    private static final Logger log = LoggerFactory.getLogger(DorisActiveTaskFacade.class);

    private final SystemAdminGuard adminGuard;
    private final DorisServiceInstanceValidator instanceValidator;
    private final DorisAdminReaderFactory readerFactory;
    private final DorisActiveTaskQueryService queryService;

    public DorisActiveTaskFacade(SystemAdminGuard adminGuard,
                                 DorisServiceInstanceValidator instanceValidator,
                                 DorisAdminReaderFactory readerFactory,
                                 DorisActiveTaskQueryService queryService) {
        this.adminGuard = adminGuard;
        this.instanceValidator = instanceValidator;
        this.readerFactory = readerFactory;
        this.queryService = queryService;
    }

    public DorisActiveTaskResponseVO query(Integer clusterId, Integer instanceId,
                                           DorisActiveTaskQueryDTO filter) {
        long deadlineNanos = System.nanoTime() + DorisActiveTaskQueryService.REQUEST_TIMEOUT_MS * 1_000_000L;
        adminGuard.requireAdmin();
        instanceValidator.requireDorisInstance(clusterId, instanceId);
        try {
            DorisAdminReaderFactory.DorisAdminConnection connection = readerFactory.create(clusterId);
            return queryService.query(clusterId, connection,
                    filter == null ? new DorisActiveTaskQueryDTO() : filter, deadlineNanos);
        } catch (DorisActiveTaskQueryService.CapabilityUnsupportedException exception) {
            throw new ResponseStatusException(NOT_IMPLEMENTED,
                    Status.DORIS_CAPABILITY_UNSUPPORTED.getMsg());
        } catch (RuntimeException exception) {
            // 对外只给固定文案（不泄露 jdbc 串），但真实原因必须留在日志里：
            // Doris 3.x 的字段不兼容曾整个伪装成「连接失败」，没有这行日志就只能靠翻 schema 反推。
            log.warn("Doris active-task query failed for cluster {} instance {}", clusterId, instanceId, exception);
            throw new ResponseStatusException(BAD_GATEWAY, Status.DORIS_CONNECT_FAILED.getMsg());
        }
    }

    public DorisActiveTaskVO queryDetail(Integer clusterId, Integer instanceId, String taskId) {
        long deadlineNanos = System.nanoTime() + DorisActiveTaskQueryService.REQUEST_TIMEOUT_MS * 1_000_000L;
        adminGuard.requireAdmin();
        instanceValidator.requireDorisInstance(clusterId, instanceId);
        try {
            DorisAdminReaderFactory.DorisAdminConnection connection = readerFactory.create(clusterId);
            return queryService.queryDetail(clusterId, connection, taskId, deadlineNanos);
        } catch (DorisActiveTaskQueryService.CapabilityUnsupportedException exception) {
            throw new ResponseStatusException(NOT_IMPLEMENTED,
                    Status.DORIS_CAPABILITY_UNSUPPORTED.getMsg());
        } catch (RuntimeException exception) {
            // 对外只给固定文案（不泄露 jdbc 串），但真实原因必须留在日志里：
            // Doris 3.x 的字段不兼容曾整个伪装成「连接失败」，没有这行日志就只能靠翻 schema 反推。
            log.warn("Doris active-task query failed for cluster {} instance {}", clusterId, instanceId, exception);
            throw new ResponseStatusException(BAD_GATEWAY, Status.DORIS_CONNECT_FAILED.getMsg());
        }
    }
}
