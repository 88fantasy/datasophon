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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.lineage.proxy.GravitinoLineageClient;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class LineageV2ControllerTest {

    private final GravitinoLineageClient client = mock(GravitinoLineageClient.class);
    private final LineageV2Controller controller = new LineageV2Controller(client);
    private final ObjectNode response = new ObjectMapper().createObjectNode();

    @Test
    void forwardsAllEightCompatibilityEndpoints() {
        when(client.get(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), anyMap(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(response);
        when(client.getJob(7L, 9L)).thenReturn(response);
        when(client.post(7L, "lineage/rebuild")).thenReturn(response);

        assertThat(controller.readiness(7L)).isSameAs(response);
        assertThat(controller.tables(7L, 1, 20, null, null, null, null)).isSameAs(response);
        assertThat(controller.graph(7L, 2L, 3, "both", "n:2:both:g8")).isSameAs(response);
        assertThat(controller.overview(7L)).isSameAs(response);
        assertThat(controller.table(7L, 2L)).isSameAs(response);
        assertThat(controller.job(7L, 9L)).isSameAs(response);
        assertThat(controller.impact(7L, 2L, 3)).isSameAs(response);
        assertThat(controller.rebuild(7L).getStatusCode().value()).isEqualTo(202);

        verify(client).get(7L, "lineage/readiness", Map.of(), false);
        verify(client).getJob(7L, 9L);
        verify(client).post(7L, "lineage/rebuild");
    }

    @Test
    void forwardsGraphParametersWithoutRenaming() {
        when(client.get(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("lineage/graph"), anyMap(),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(response);
        controller.graph(7L, 2L, 3, "downstream", "n:2:down:g8");
        verify(client).get(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("lineage/graph"),
                org.mockito.ArgumentMatchers.argThat(query ->
                        query.get("rootNodeId").equals(2L)
                                && query.get("depth").equals(3)
                                && query.get("direction").equals("downstream")
                                && query.get("expand").equals("n:2:down:g8")),
                org.mockito.ArgumentMatchers.eq(true));
    }
}
