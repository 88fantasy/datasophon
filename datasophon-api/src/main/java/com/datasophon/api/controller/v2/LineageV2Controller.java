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

package com.datasophon.api.controller.v2;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.lineage.LineageIngestOperations;
import com.datasophon.api.lineage.LineageIngestService.IngestResult;
import com.datasophon.api.lineage.LineageLeaseGuard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/** OpenLineage-compatible v2 ingest endpoint. Query endpoints belong to the next delivery batch. */
@RestController
@RequestMapping("/v2")
public class LineageV2Controller extends ApiController {

    private final LineageIngestOperations ingestService;
    private final LineageLeaseGuard leaseGuard;

    public LineageV2Controller(LineageIngestOperations ingestService, LineageLeaseGuard leaseGuard) {
        this.ingestService = ingestService;
        this.leaseGuard = leaseGuard;
    }

    // TODO L2: 接 Gravitino 时补共享 token 校验。
    @PostMapping("/lineage")
    public IngestResult ingest(@RequestParam long clusterId, @RequestBody JsonNode payload) {
        leaseGuard.requireOwner();
        try {
            return ingestService.ingest(clusterId, payload);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/lineage/readiness")
    public ResponseEntity<LeaseReadiness> readiness() {
        boolean owner = leaseGuard.isOwner();
        LeaseReadiness body = owner
                ? new LeaseReadiness(true, "UP", "Lineage Master lease is held")
                : new LeaseReadiness(false, "DOWN", LineageLeaseGuard.UNAVAILABLE_MESSAGE);
        return ResponseEntity.status(owner ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    public record LeaseReadiness(boolean owner, String status, String message) {
    }
}
