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

import static com.datasophon.api.utils.HttpUriUtils.encode;
import static com.datasophon.api.utils.HttpUriUtils.resolve;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Small JSON client for the Gravitino native lineage API. */
@Component
public class GravitinoLineageClient {

    private static final Logger LOG = LoggerFactory.getLogger(GravitinoLineageClient.class);

    private final GravitinoLineageEndpointResolver endpointResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String authToken;

    public GravitinoLineageClient(GravitinoLineageEndpointResolver endpointResolver,
                                  ObjectMapper objectMapper,
                                  @Value("${datasophon.lineage.proxy.connect-timeout-ms:3000}") long connectTimeoutMs,
                                  @Value("${datasophon.lineage.proxy.request-timeout-ms:10000}") long requestTimeoutMs,
                                  @Value("${datasophon.lineage.proxy.auth-token:}") String authToken) {
        if (connectTimeoutMs <= 0 || requestTimeoutMs <= 0) {
            throw new IllegalArgumentException("lineage proxy timeouts must be positive");
        }
        this.endpointResolver = endpointResolver;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.authToken = authToken;
    }

    public JsonNode get(long clusterId, String resource, Map<String, ?> query, NodeInjection injection) {
        JsonNode response = exchange(clusterId, resource, query, "GET");
        injection.apply(response, clusterId);
        return response;
    }

    public JsonNode getJob(long clusterId, long jobId) {
        JsonNode response = get(clusterId, "lineage/job/" + jobId, Map.of(), NodeInjection.ROOT);
        enrichFlinkStreamingJob(response);
        return response;
    }

    public JsonNode getRunByExternalKey(long clusterId, String externalRunKey) {
        return get(clusterId, "lineage/run/by-external-key/" + encode(externalRunKey),
                Map.of(), NodeInjection.NONE);
    }

    public JsonNode post(long clusterId, String resource) {
        return exchange(clusterId, resource, Map.of(), "POST");
    }

    private JsonNode exchange(long clusterId, String resource, Map<String, ?> query, String method) {
        if (StringUtils.isBlank(authToken)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "datasophon.lineage.proxy.auth-token is not configured; it must be the same "
                            + "static JWT configured on the GRAVITINO role as "
                            + "gravitino.authenticator.oauth.defaultSignKey's issued token");
        }
        URI endpoint = endpointResolver.resolve(clusterId);
        URI uri = resolve(endpoint, resource, query);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + authToken);
        if ("POST".equals(method)) {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.GET();
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return StringUtils.isBlank(response.body())
                        ? objectMapper.createObjectNode()
                        : objectMapper.readTree(response.body());
            }
            if (status == 400 || status == 404 || status == 409 || status == 503) {
                throw new ResponseStatusException(
                        HttpStatus.valueOf(status), downstreamMessage(response.body(), status));
            }
            if (status >= 500) {
                // Gravitino's message at this point is an unstructured internal error (SQL
                // exception text, JDBC URL fragments, ...). Log it for operators but never hand
                // it to the caller.
                LOG.warn("Unexpected Gravitino lineage failure, status={}, body={}", status, response.body());
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Gravitino lineage endpoint returned an unexpected error");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "unexpected Gravitino lineage status " + status);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Gravitino lineage request was interrupted", e);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Gravitino lineage endpoint is unavailable", e);
        }
    }

    private String downstreamMessage(String body, int status) {
        if (StringUtils.isBlank(body)) {
            return "Gravitino lineage request failed with status " + status;
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            for (String field : new String[]{"message", "errorMessage", "error"}) {
                if (json.hasNonNull(field) && !json.path(field).asText().isBlank()) {
                    return json.path(field).asText();
                }
            }
        } catch (IOException ignored) {
            // The status mapping remains authoritative when a proxy or servlet emits non-JSON text.
        }
        return "Gravitino lineage request failed with status " + status;
    }

    /**
     * Fills in the standard job facets for a running Flink job only when the producer did not
     * supply them. The T6/T9 emitter carries its Flink JobID through the legacy
     * {@code spark_properties} compatibility facet, so historical events have no
     * {@code engine} or {@code jobType} values for Gravitino to return — both come back
     * as {@code UNKNOWN}, which is the only case this fills in.
     *
     * <p>TODO: delete once the emitter sets the {@code processing_engine} facet on every event
     * and the events predating that change have aged out of Gravitino. Until then this stays,
     * but note the cost of keeping it: a Flink job whose engine/type is genuinely unknown gets
     * a fabricated value here, and the UI cannot tell that apart from a real one.
     */
    private static void enrichFlinkStreamingJob(JsonNode response) {
        if (!(response instanceof ObjectNode job) || !isFlinkNamespace(job.path("jobName").asText())) {
            return;
        }
        if ("UNKNOWN".equalsIgnoreCase(job.path("engine").asText())) {
            job.put("engine", "FLINK");
        }
        if ("UNKNOWN".equalsIgnoreCase(job.path("jobType").asText())
                && "RUNNING".equalsIgnoreCase(job.path("state").asText())) {
            job.put("jobType", "STREAMING");
        }
    }

    private static boolean isFlinkNamespace(String jobName) {
        int namespaceEnd = jobName.indexOf('.');
        if (namespaceEnd <= 0) {
            return false;
        }
        String namespace = jobName.substring(0, namespaceEnd).toLowerCase(Locale.ROOT);
        return namespace.equals("flink") || namespace.startsWith("flink-") || namespace.startsWith("flink_");
    }

    /**
     * Where in a response body {@code clusterId} must be injected into lineage node objects.
     * Each endpoint's response shape is fixed by {@code LineageQuery}, so the path is explicit
     * per endpoint rather than inferred by walking the whole tree and guessing which objects
     * "look like" a node (that heuristic both over-matches any future field named {@code id} +
     * {@code canonicalName}, and silently no-ops if a real node is missing either field).
     */
    public enum NodeInjection {
        /** No node objects in this response need a clusterId (e.g. overview, rebuild). */
        NONE {
            @Override
            void apply(JsonNode response, long clusterId) {
                // nothing to inject
            }
        },
        /** {@code TablePage}: an array of nodes at {@code /data/list}. */
        TABLE_LIST {
            @Override
            void apply(JsonNode response, long clusterId) {
                injectArray(response.at("/data/list"), clusterId);
            }
        },
        /** {@code GraphData}: an array of nodes at {@code /data/nodes}. */
        GRAPH_NODES {
            @Override
            void apply(JsonNode response, long clusterId) {
                injectArray(response.at("/data/nodes"), clusterId);
            }
        },
        /** {@code NodeMeta}: the single node object at {@code /data}. */
        SINGLE_TABLE {
            @Override
            void apply(JsonNode response, long clusterId) {
                injectNode(response.at("/data"), clusterId);
            }
        },
        /** A non-node payload (e.g. job detail) that itself is the response root. */
        ROOT {
            @Override
            void apply(JsonNode response, long clusterId) {
                if (response instanceof ObjectNode object) {
                    object.put("clusterId", clusterId);
                }
            }
        };

        abstract void apply(JsonNode response, long clusterId);
    }

    private static void injectArray(JsonNode array, long clusterId) {
        if (!array.isArray()) {
            LOG.warn("Expected an array of Gravitino lineage nodes, got: {}", array.getNodeType());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gravitino lineage response is missing expected node fields");
        }
        array.elements().forEachRemaining(node -> injectNode(node, clusterId));
    }

    private static void injectNode(JsonNode node, long clusterId) {
        if (!(node instanceof ObjectNode object) || !object.has("id") || !object.has("canonicalName")) {
            LOG.warn("Expected a Gravitino lineage node with id/canonicalName, got: {}", node);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gravitino lineage response is missing expected node fields");
        }
        object.put("clusterId", clusterId);
    }
}
