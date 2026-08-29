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

import static com.datasophon.api.utils.HttpUriUtils.resolve;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Small authenticated JSON client for DolphinScheduler 3.4 Open API. */
@Component
public class DsApiClient {

    // token/endpoint 在页面生命周期内稳定不变，短 TTL 缓存避免每次 get() 都重复 4 次数据库往返，
    // 同时保证配置变更后能在一个轮询周期（DsDagPage 每 15s 轮询一次）左右的时间内生效。
    private static final Duration RESOLUTION_CACHE_TTL = Duration.ofSeconds(30);

    private final DsEndpointResolver endpointResolver;
    private final DsConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Map<Integer, CachedResolution> resolutionCache = new ConcurrentHashMap<>();

    public DsApiClient(DsEndpointResolver endpointResolver,
                       DsConfigService configService,
                       ObjectMapper objectMapper,
                       @Value("${datasophon.ds.proxy.connect-timeout-ms:3000}") long connectTimeoutMs,
                       @Value("${datasophon.ds.proxy.request-timeout-ms:5000}") long requestTimeoutMs) {
        if (connectTimeoutMs <= 0 || requestTimeoutMs <= 0) {
            throw new IllegalArgumentException("DS proxy timeouts must be positive");
        }
        this.endpointResolver = endpointResolver;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
    }

    public JsonNode get(Integer clusterId, String resource, Map<String, ?> query) {
        CachedResolution resolution = resolution(clusterId);
        URI uri = resolve(resolution.endpoint(), resource, query);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("token", resolution.token())
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return validate(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DS Open API 请求被中断", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DS Open API 不可达或请求超时", e);
        }
    }

    private CachedResolution resolution(Integer clusterId) {
        CachedResolution cached = resolutionCache.get(clusterId);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached;
        }
        CachedResolution fresh = new CachedResolution(
                configService.apiToken(clusterId), endpointResolver.resolve(clusterId), now.plus(RESOLUTION_CACHE_TTL));
        resolutionCache.put(clusterId, fresh);
        return fresh;
    }

    private record CachedResolution(String token, URI endpoint, Instant expiresAt) {
    }

    private JsonNode validate(HttpResponse<String> response) {
        if (response.statusCode() == 401) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "DS apiToken 已失效");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "DS Open API 返回异常状态 " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase().contains("json")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DS Open API 返回了非 JSON 响应");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DS Open API 返回的 JSON 无法解析", e);
        }
        if (root == null || !root.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DS Open API 响应结构无效");
        }
        if (root.path("code").asInt(-1) != 0) {
            String message = root.path("msg").asText("DS Open API 请求失败");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
        }
        return root.path("data");
    }

}
