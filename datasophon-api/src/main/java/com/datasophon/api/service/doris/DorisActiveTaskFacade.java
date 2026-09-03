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

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Orchestrates authorization, instance validation, connection, and active-task querying. */
@Service
public class DorisActiveTaskFacade {

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
        adminGuard.requireAdmin();
        instanceValidator.requireDorisInstance(clusterId, instanceId);
        try {
            DorisAdminReaderFactory.DorisAdminConnection connection = readerFactory.create(clusterId);
            return queryService.query(clusterId, connection,
                    filter == null ? new DorisActiveTaskQueryDTO() : filter);
        } catch (DorisActiveTaskQueryService.CapabilityUnsupportedException exception) {
            throw new ResponseStatusException(NOT_IMPLEMENTED,
                    Status.DORIS_CAPABILITY_UNSUPPORTED.getMsg());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, Status.DORIS_CONNECT_FAILED.getMsg());
        }
    }

    public DorisActiveTaskVO queryDetail(Integer clusterId, Integer instanceId, String taskId) {
        adminGuard.requireAdmin();
        instanceValidator.requireDorisInstance(clusterId, instanceId);
        try {
            DorisAdminReaderFactory.DorisAdminConnection connection = readerFactory.create(clusterId);
            return queryService.queryDetail(clusterId, connection, taskId);
        } catch (DorisActiveTaskQueryService.CapabilityUnsupportedException exception) {
            throw new ResponseStatusException(NOT_IMPLEMENTED,
                    Status.DORIS_CAPABILITY_UNSUPPORTED.getMsg());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, Status.DORIS_CONNECT_FAILED.getMsg());
        }
    }
}
