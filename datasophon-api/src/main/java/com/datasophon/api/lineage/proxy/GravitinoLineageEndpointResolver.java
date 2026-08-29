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

import com.datasophon.api.service.ServiceRoleEndpointResolver;
import com.datasophon.api.service.ServiceRoleEndpointResolver.EndpointResolutionException;
import com.datasophon.api.service.ServiceRoleEndpointResolver.HostPort;
import com.datasophon.dao.enums.ServiceRoleState;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the unique running GravitinoServer endpoint for one Datasophon cluster. */
@Component
public class GravitinoLineageEndpointResolver {

    static final String ROLE_NAME = "GravitinoServer";
    static final String HTTP_PORT_PARAM = "gravitino.server.webserver.httpPort";

    private final ServiceRoleEndpointResolver endpointResolver;

    public GravitinoLineageEndpointResolver(ServiceRoleEndpointResolver endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    public URI resolve(long clusterId) {
        if (clusterId <= 0 || clusterId > Integer.MAX_VALUE) {
            throw unavailable("invalid clusterId");
        }
        HostPort endpoint;
        try {
            endpoint = endpointResolver.resolve(
                    (int) clusterId, ROLE_NAME, HTTP_PORT_PARAM, Set.of(ServiceRoleState.RUNNING));
        } catch (EndpointResolutionException e) {
            throw unavailable(switch (e.failure()) {
                case NO_USABLE_ROLE -> "no running GravitinoServer instance";
                case MULTIPLE_USABLE_ROLES -> "multiple running GravitinoServer instances";
                case HOST_UNAVAILABLE -> "GravitinoServer host is unavailable";
                case PORT_UNAVAILABLE -> "GravitinoServer HTTP port is unavailable";
            });
        }
        try {
            return new URI("http", null, endpoint.host(), endpoint.port(), "/api/", null, null);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "invalid GravitinoServer endpoint", e);
        }
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
