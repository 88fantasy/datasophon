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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class DsApiClientTest {

    private final DsEndpointResolver resolver = mock(DsEndpointResolver.class);
    private final DsConfigService configService = mock(DsConfigService.class);
    private final AtomicReference<String> tokenHeader = new AtomicReference<>();
    private final AtomicReference<String> rawQuery = new AtomicReference<>();
    private HttpServer server;
    private DsApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/dolphinscheduler/ok", exchange -> {
            tokenHeader.set(exchange.getRequestHeaders().getFirst("token"));
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "application/json", "{\"code\":0,\"msg\":\"success\",\"data\":{\"id\":1}}");
        });
        server.createContext("/dolphinscheduler/unauthorized", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.createContext("/dolphinscheduler/html", exchange -> respond(exchange, 200, "text/html", "<html>DS UI</html>"));
        server.createContext("/dolphinscheduler/slow", exchange -> {
            try {
                Thread.sleep(200);
                respond(exchange, 200, "application/json", "{\"code\":0,\"data\":{}}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        when(resolver.resolve(7)).thenReturn(URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/dolphinscheduler/"));
        when(configService.apiToken(7)).thenReturn("readonly-token");
        client = new DsApiClient(resolver, configService, new ObjectMapper(), 1000, 3000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void acceptsJsonAndInjectsToken() {
        assertThat(client.get(7, "ok", Map.of()).path("id").asInt()).isEqualTo(1);
        assertThat(tokenHeader.get()).isEqualTo("readonly-token");
    }

    @Test
    void encodesQueryParametersAndSkipsBlankValues() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("name", "a b");
        query.put("symbol", "x/y");
        query.put("blank", " ");
        query.put("missing", null);

        client.get(7, "ok", query);

        assertThat(rawQuery.get()).isEqualTo("name=a%20b&symbol=x%2Fy");
    }

    @Test
    void mapsEmpty401BeforeParsingBody() {
        assertReadableError("unauthorized", 401, "apiToken 已失效");
    }

    @Test
    void rejectsHtmlReturnedWith200() {
        assertReadableError("html", 502, "非 JSON");
    }

    @Test
    void mapsRequestTimeout() {
        DsApiClient timeoutClient = new DsApiClient(resolver, configService, new ObjectMapper(), 1000, 50);
        assertReadableError(timeoutClient, "slow", 502, "不可达或请求超时");
    }

    private void assertReadableError(String resource, int status, String message) {
        assertReadableError(client, resource, status, message);
    }

    private void assertReadableError(DsApiClient testedClient, String resource, int status, String message) {
        assertThatThrownBy(() -> testedClient.get(7, resource, Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException response = (ResponseStatusException) error;
                    assertThat(response.getStatusCode().value()).isEqualTo(status);
                    assertThat(response.getReason()).contains(message);
                });
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
