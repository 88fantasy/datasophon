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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datasophon.api.lineage.LineageIngestOperations;
import com.datasophon.api.lineage.LineageIngestService.IngestResult;
import com.datasophon.api.lineage.LineageIngestService.Status;
import com.datasophon.api.lineage.LineageLeaseGuard;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;

@WebMvcTest(useDefaultFilters = false)
@Import({LineageV2ControllerTest.WebConfiguration.class, V2ResponseBodyAdvice.class, V2ApiExceptionHandler.class})
@TestExecutionListeners(listeners = {
        ServletTestExecutionListener.class,
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class
}, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class LineageV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final AtomicReference<BiFunction<Long, JsonNode, IngestResult>> HANDLER =
            new AtomicReference<>();
    private static final AtomicBoolean LEASE_OWNER = new AtomicBoolean();

    @BeforeEach
    void resetMockService() {
        LEASE_OWNER.set(true);
        HANDLER.set((clusterId, payload) -> new IngestResult(Status.CHANGED, 11L, 3, 2, 3));
    }

    @Test
    void ingestRequiresClusterIdAndReturnsAdviceWrappedPojo() throws Exception {
        mockMvc.perform(post("/v2/lineage")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CHANGED"))
                .andExpect(jsonPath("$.data.jobId").value(11))
                .andExpect(jsonPath("$.data.definitionVersion").value(3));

        mockMvc.perform(post("/v2/lineage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestMapsInvalidPayloadToBadRequest() throws Exception {
        HANDLER.set((clusterId, payload) -> {
            throw new IllegalArgumentException("producer must not be blank");
        });

        mockMvc.perform(post("/v2/lineage")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonOwnerRejectsIngestAndReportsReadinessDown() throws Exception {
        LEASE_OWNER.set(false);

        mockMvc.perform(post("/v2/lineage")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/v2/lineage/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.owner").value(false))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.message").value(LineageLeaseGuard.UNAVAILABLE_MESSAGE));
    }

    @Test
    void ownerReadinessIsUp() throws Exception {
        mockMvc.perform(get("/v2/lineage/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Configuration(proxyBeanMethods = false)
    static class WebConfiguration {

        @Bean
        LineageV2Controller lineageV2Controller(
                                                LineageIngestOperations ingestService,
                                                LineageLeaseGuard leaseGuard) {
            return new LineageV2Controller(ingestService, leaseGuard);
        }

        @Bean
        LineageIngestOperations ingestService() {
            return (clusterId, payload) -> HANDLER.get().apply(clusterId, payload);
        }

        @Bean
        LineageLeaseGuard lineageLeaseGuard() {
            return new LineageLeaseGuard(LEASE_OWNER::get);
        }
    }
}
