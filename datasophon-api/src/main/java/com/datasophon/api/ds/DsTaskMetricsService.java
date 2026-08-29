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

package com.datasophon.api.ds;

import com.datasophon.api.dto.v2.DsDagNodeVO;
import com.datasophon.api.dto.v2.DsTaskMetricsVO;

import java.util.Set;

import org.springframework.stereotype.Service;

/** Dispatches a DS task instance to its batch or streaming metric provider. */
@Service
public class DsTaskMetricsService {

    private static final Set<String> TERMINAL_STATES = Set.of("SUCCESS", "FAILURE", "KILL", "STOP");

    private final DsBatchMetricsProvider batchMetricsProvider;
    private final DsStreamMetricsProvider streamMetricsProvider;

    public DsTaskMetricsService(DsBatchMetricsProvider batchMetricsProvider,
                                DsStreamMetricsProvider streamMetricsProvider) {
        this.batchMetricsProvider = batchMetricsProvider;
        this.streamMetricsProvider = streamMetricsProvider;
    }

    public DsTaskMetricsVO metrics(Integer clusterId, DsDagNodeVO node) {
        if (node.getTaskInstanceId() == null) {
            throw new NotBoundException();
        }
        return "STREAM".equals(node.getFlowType())
                ? streamMetricsProvider.metrics(clusterId, node.getTaskInstanceId(), isEnded(node))
                : batchMetricsProvider.metrics(clusterId, node.getTaskInstanceId());
    }

    private static boolean isEnded(DsDagNodeVO node) {
        return (node.getEndTime() != null && !node.getEndTime().isBlank())
                || (node.getState() != null && TERMINAL_STATES.contains(node.getState()));
    }

    public static class NotBoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static class JobEndedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
