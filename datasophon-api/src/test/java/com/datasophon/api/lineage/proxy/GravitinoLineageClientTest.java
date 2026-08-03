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

package com.datasophon.api.lineage.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class GravitinoLineageClientTest {

    private final GravitinoLineageEndpointResolver resolver =
            mock(GravitinoLineageEndpointResolver.class);
    private final AtomicReference<String> rawQuery = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private HttpServer server;
    private GravitinoLineageClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/lineage/graph", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200,
                    "{\"data\":{\"nodes\":[{\"id\":2,\"canonicalName\":\"c.d.t\"}],"
                            + "\"edges\":[],\"collapsed\":[],\"truncated\":false}}");
        });
        server.createContext("/api/lineage/job/9", exchange -> respond(exchange, 200,
                "{\"id\":9,\"jobName\":\"daily job\",\"engine\":\"UNKNOWN\"}"));
        server.createContext("/api/lineage/bad-request", exchange -> respond(exchange, 400, "{\"message\":\"bad depth\"}"));
        server.createContext("/api/lineage/crash", exchange -> respond(exchange, 500, "{\"message\":\"boom\"}"));
        server.createContext("/api/lineage/broken-node", exchange -> respond(exchange, 200,
                "{\"data\":{\"nodes\":[{\"id\":2}],\"edges\":[],\"collapsed\":[],\"truncated\":false}}"));
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
        when(resolver.resolve(7L)).thenReturn(base);
        client = new GravitinoLineageClient(resolver, new ObjectMapper(), 1000, 3000, "test-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void encodesParametersAndInjectsClusterIdIntoNodesAndJobs() {
        JsonNode graph = client.get(7L, "lineage/graph",
                Map.of("rootNodeId", 2, "expand", "n:2:both:g8", "keyword", "a b"),
                GravitinoLineageClient.NodeInjection.GRAPH_NODES);
        assertThat(rawQuery.get()).contains("keyword=a%20b").contains("expand=n%3A2%3Aboth%3Ag8");
        assertThat(graph.at("/data/nodes/0/clusterId").asLong()).isEqualTo(7L);
        assertThat(authorizationHeader.get()).isEqualTo("Bearer test-token");

        JsonNode job = client.getJob(7L, 9L);
        assertThat(job.path("clusterId").asLong()).isEqualTo(7L);
    }

    @Test
    void allowsConstructionWithBlankAuthTokenButFailsRequestsAsServiceUnavailable() {
        GravitinoLineageClient blankTokenClient =
                new GravitinoLineageClient(resolver, new ObjectMapper(), 1000, 3000, " ");
        assertStatus(() -> blankTokenClient.get(7L, "lineage/graph", Map.of(),
                GravitinoLineageClient.NodeInjection.NONE), 503);
    }

    @Test
    void failsLoudlyWhenExpectedNodeFieldsAreMissing() {
        assertStatus(() -> client.get(7L, "lineage/broken-node", Map.of(),
                GravitinoLineageClient.NodeInjection.GRAPH_NODES), 502);
    }

    @Test
    void mapsExpectedErrorsAndUnexpectedServerFailures() {
        assertStatus(() -> client.get(7L, "lineage/bad-request", Map.of(),
                GravitinoLineageClient.NodeInjection.NONE), 400);
        assertStatus(() -> client.get(7L, "lineage/crash", Map.of(),
                GravitinoLineageClient.NodeInjection.NONE), 502);
    }

    @Test
    void doesNotLeakUpstream5xxMessageToCaller() {
        assertThatThrownBy(() -> client.get(7L, "lineage/crash", Map.of(),
                GravitinoLineageClient.NodeInjection.NONE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getReason())
                .isNotNull()
                .satisfies(reason -> assertThat((String) reason).doesNotContain("boom"));
    }

    @Test
    void mapsConnectionFailureToServiceUnavailable() {
        server.stop(0);
        assertStatus(() -> client.get(7L, "lineage/graph", Map.of(),
                GravitinoLineageClient.NodeInjection.NONE), 503);
    }

    private static void assertStatus(Runnable action, int expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(expected);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
