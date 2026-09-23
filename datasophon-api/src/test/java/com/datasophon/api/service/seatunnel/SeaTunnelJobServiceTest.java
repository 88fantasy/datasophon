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

package com.datasophon.api.service.seatunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ServiceInstancePortResolver;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class SeaTunnelJobServiceTest {

    @Test
    void retriesNextMasterWhenFirstConnectionIsRefused() throws IOException {
        try (TestServer second = new TestServer(responding(200, "{\"source\":\"second\"}"))) {
            int refusedPort = unusedPort();
            Fixture fixture = fixture(List.of(master(1, "127.0.0.1"), master(2, "127.0.0.1")),
                    Map.of(1, refusedPort, 2, second.port()));

            JSONObject result = (JSONObject) fixture.service.overview(7, 8);

            assertThat(result.getString("source")).isEqualTo("second");
        }
    }

    @Test
    void retriesNextMasterWhenFirstReturnsServerError() throws IOException {
        AtomicInteger firstRequests = new AtomicInteger();
        try (TestServer first = new TestServer(exchange -> {
            firstRequests.incrementAndGet();
            respond(exchange, 503, "unavailable");
        });
                TestServer second = new TestServer(responding(200, "{\"source\":\"second\"}"))) {
            Fixture fixture = fixture(List.of(master(1, "127.0.0.1"), master(2, "127.0.0.1")),
                    Map.of(1, first.port(), 2, second.port()));

            JSONObject result = (JSONObject) fixture.service.overview(7, 8);

            assertThat(firstRequests).hasValue(1);
            assertThat(result.getString("source")).isEqualTo("second");
        }
    }

    @Test
    void reportsAllMasterAddressesWhenEveryConnectionFails() throws IOException {
        int firstPort = unusedPort();
        int secondPort = unusedPort();
        Fixture fixture = fixture(List.of(master(1, "127.0.0.1"), master(2, "127.0.0.1")),
                Map.of(1, firstPort, 2, secondPort));

        assertThatThrownBy(() -> fixture.service.overview(7, 8))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(BAD_GATEWAY);
                    assertThat(exception.getReason()).isEqualTo(
                            "SeaTunnel master 均不可达: 127.0.0.1:" + firstPort + ", 127.0.0.1:" + secondPort);
                });
    }

    @Test
    void overviewRemovesOnlyUnassignedSlot() throws IOException {
        String body = "{\"projectVersion\":\"3.0.0\",\"gitCommitAbbrev\":\"abc123\","
                + "\"totalSlot\":\"8\",\"runningJobs\":\"2\",\"failedJobs\":\"1\","
                + "\"workers\":[{\"address\":\"worker-1\",\"usedSlots\":\"2\"}],"
                + "\"unassignedSlot\":\"6\"}";
        try (TestServer server = new TestServer(responding(200, body))) {
            Fixture fixture = fixture(List.of(master(1, "127.0.0.1")), Map.of(1, server.port()));

            JSONObject result = (JSONObject) fixture.service.overview(7, 8);

            assertThat(result).containsEntry("projectVersion", "3.0.0")
                    .containsEntry("gitCommitAbbrev", "abc123")
                    .containsEntry("totalSlot", "8")
                    .containsEntry("runningJobs", "2")
                    .containsEntry("failedJobs", "1")
                    .doesNotContainKey("unassignedSlot");
            assertThat(result.getJSONArray("workers").getJSONObject(0).getString("usedSlots")).isEqualTo("2");
        }
    }

    @Test
    void endpointsMapToZetaPathsAndEncodeJobIdsAsPathSegments() throws IOException {
        List<String> paths = new java.util.concurrent.CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer(exchange -> {
            paths.add(exchange.getRequestURI().toASCIIString());
            respond(exchange, 200, "{}");
        })) {
            Fixture fixture = fixture(List.of(master(1, "127.0.0.1")), Map.of(1, server.port()));

            fixture.service.workers(7, 8);
            fixture.service.pending(7, 8);
            fixture.service.jobs(7, 8, "running");
            fixture.service.jobs(7, 8, "finished");
            fixture.service.jobInfo(7, 8, "job/1");

            assertThat(paths).containsExactly(
                    "/resource/workers", "/pending-jobs", "/running-jobs", "/finished-jobs", "/job-info/job%2F1");
        }
    }

    @Test
    void passesClientErrorsThroughWithoutTryingAnotherMaster() throws IOException {
        AtomicInteger secondRequests = new AtomicInteger();
        try (
                TestServer first = new TestServer(responding(404, "missing job"));
                TestServer second = new TestServer(exchange -> {
                    secondRequests.incrementAndGet();
                    respond(exchange, 200, "{}");
                })) {
            Fixture fixture = fixture(List.of(master(1, "127.0.0.1"), master(2, "127.0.0.1")),
                    Map.of(1, first.port(), 2, second.port()));

            assertThatThrownBy(() -> fixture.service.jobInfo(7, 8, "missing"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(NOT_FOUND);
                        assertThat(exception.getReason()).isEqualTo("missing job");
                    });
            assertThat(secondRequests).hasValue(0);
        }
    }

    @Test
    void rejectsInstanceFromOtherClusterOrServiceBeforeCallingMasters() {
        Fixture fixture = fixture(List.of(master(1, "127.0.0.1")), Map.of());

        for (int instanceId : new int[]{9, 10, 404}) {
            assertThatThrownBy(() -> fixture.service.overview(7, instanceId))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            exception -> assertThat(exception.getStatusCode()).isEqualTo(BAD_REQUEST));
        }
    }

    @Test
    void reportsNotFoundWhenClusterHasNoMaster() {
        Fixture fixture = fixture(List.of(), Map.of());

        assertThatThrownBy(() -> fixture.service.overview(7, 8))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(NOT_FOUND));
    }

    @Test
    void masterEndpointUsesConfiguredPortOrDefault() {
        Fixture fixture = fixture(List.of(master(1, "master-a"), master(2, "master-b")), Map.of(1, 18089));

        assertThat(fixture.service.masterEndpoints(7))
                .extracting(endpoint -> endpoint.host() + ":" + endpoint.port())
                .containsExactly("master-a:18089", "master-b:18088");
    }

    private static Fixture fixture(List<ClusterServiceRoleInstanceEntity> masters, Map<Integer, Integer> ports) {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "SeaTunnelMaster"))
                .thenReturn(masters);
        ServiceInstancePortResolver portResolver = mock(ServiceInstancePortResolver.class);
        when(portResolver.portsOf(any(ClusterServiceRoleInstanceEntity.class))).thenAnswer(invocation -> {
            ClusterServiceRoleInstanceEntity role = invocation.getArgument(0);
            Integer port = ports.get(role.getId());
            return port == null ? List.of() : List.of(new RolePort("masterHttpPort", "HTTP", port));
        });
        ClusterServiceInstanceService instanceService = mock(ClusterServiceInstanceService.class);
        when(instanceService.getById(8)).thenReturn(instance(7, "SEATUNNEL"));
        when(instanceService.getById(9)).thenReturn(instance(7, "DORIS"));
        when(instanceService.getById(10)).thenReturn(instance(99, "SEATUNNEL"));
        return new Fixture(new SeaTunnelJobService(instanceService, roleService, portResolver));
    }

    private static ClusterServiceInstanceEntity instance(int clusterId, String serviceName) {
        ClusterServiceInstanceEntity instance = new ClusterServiceInstanceEntity();
        instance.setClusterId(clusterId);
        instance.setServiceName(serviceName);
        return instance;
    }

    private static ClusterServiceRoleInstanceEntity master(int id, String hostname) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setId(id);
        role.setHostname(hostname);
        role.setServiceRoleName("SeaTunnelMaster");
        return role;
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static HttpHandler responding(int status, String body) {
        return exchange -> respond(exchange, status, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record Fixture(SeaTunnelJobService service) {
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        TestServer(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", handler);
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
