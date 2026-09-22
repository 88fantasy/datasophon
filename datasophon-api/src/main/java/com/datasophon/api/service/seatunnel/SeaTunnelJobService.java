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

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ServiceInstancePortResolver;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/** Read-only proxy to the SeaTunnel Zeta masters in a cluster. */
@Service
public class SeaTunnelJobService {

    private static final String MASTER_ROLE = "SeaTunnelMaster";
    private static final String MASTER_HTTP_PORT = "masterHttpPort";
    private static final int DEFAULT_MASTER_HTTP_PORT = 18088;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ClusterServiceRoleInstanceService roleService;
    private final ServiceInstancePortResolver portResolver;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public SeaTunnelJobService(ClusterServiceRoleInstanceService roleService,
                               ServiceInstancePortResolver portResolver) {
        this.roleService = roleService;
        this.portResolver = portResolver;
    }

    public Object overview(Integer clusterId) {
        return get(clusterId, "/overview", true);
    }

    public Object workers(Integer clusterId) {
        return get(clusterId, "/resource/workers", false);
    }

    public Object pending(Integer clusterId) {
        return get(clusterId, "/pending-jobs", false);
    }

    public Object jobs(Integer clusterId, String state) {
        String path = switch (state) {
            case "running" -> "/running-jobs";
            case "finished" -> "/finished-jobs";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "state 仅支持 running 或 finished");
        };
        return get(clusterId, path, false);
    }

    public Object jobInfo(Integer clusterId, String jobId) {
        return get(clusterId, "/job-info/" + encodePathSegment(jobId), false);
    }

    List<MasterEndpoint> masterEndpoints(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> roles = roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, MASTER_ROLE);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .filter(role -> role.getHostname() != null && !role.getHostname().isBlank())
                .map(role -> new MasterEndpoint(role.getHostname(), portOf(role)))
                .toList();
    }

    private int portOf(ClusterServiceRoleInstanceEntity role) {
        List<RolePort> ports = portResolver.portsOf(role);
        if (ports == null) {
            return DEFAULT_MASTER_HTTP_PORT;
        }
        return ports.stream()
                .filter(port -> MASTER_HTTP_PORT.equals(port.paramName()))
                .map(RolePort::port)
                .findFirst()
                .orElse(DEFAULT_MASTER_HTTP_PORT);
    }

    private Object get(Integer clusterId, String path, boolean removeUnassignedSlot) {
        List<MasterEndpoint> masters = masterEndpoints(clusterId);
        for (MasterEndpoint master : masters) {
            HttpRequest request = HttpRequest.newBuilder(uri(master, path))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "SeaTunnel master 请求被中断", e);
            }

            if (response.statusCode() >= 500) {
                continue;
            }
            if (response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(response.statusCode()), response.body());
            }
            return parseResponse(response.body(), removeUnassignedSlot);
        }
        String addresses = masters.stream()
                .map(MasterEndpoint::address)
                .collect(Collectors.joining(", "));
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "SeaTunnel master 均不可达: " + addresses);
    }

    private static Object parseResponse(String body, boolean removeUnassignedSlot) {
        Object data;
        try {
            data = JSON.parse(body);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SeaTunnel master 返回无效 JSON", e);
        }
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SeaTunnel master 返回空 JSON");
        }
        if (removeUnassignedSlot) {
            if (!(data instanceof JSONObject object)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SeaTunnel master overview 不是对象");
            }
            object.remove("unassignedSlot");
        }
        return data;
    }

    private static URI uri(MasterEndpoint master, String path) {
        String host = master.host().contains(":") && !master.host().startsWith("[")
                ? "[" + master.host() + "]"
                : master.host();
        return URI.create("http://" + host + ":" + master.port() + path);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    record MasterEndpoint(String host, int port) {

        String address() {
            return host + ":" + port;
        }
    }
}
