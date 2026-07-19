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

package com.datasophon.api.controller.internal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datasophon.api.load.LoadServiceMeta;
import com.datasophon.api.load.MetaReloadResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(useDefaultFilters = false)
@Import({InternalResponseBodyAdvice.class, InternalApiExceptionHandler.class})
class InternalMetaControllerTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoadServiceMeta loadServiceMeta;

    @Test
    void refresh_wrapsReloadResultInInternalResponse() throws Exception {
        MetaReloadResult result = new MetaReloadResult();
        result.setPhysicalTotal(2);
        result.setPhysicalLoaded(2);
        result.setK8sTotal(1);
        result.setK8sLoaded(1);
        when(loadServiceMeta.reloadAllMeta()).thenReturn(result);

        mockMvc.perform(post("/internal/meta/refresh").header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.physicalTotal").value(2))
                .andExpect(jsonPath("$.data.k8sLoaded").value(1));
    }

    @Test
    void refresh_convertsUnexpectedExceptionToInternalResponse() throws Exception {
        when(loadServiceMeta.reloadAllMeta()).thenThrow(new RuntimeException("reload failed"));

        mockMvc.perform(post("/internal/meta/refresh").header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("reload failed"));
    }

    @Test
    void refresh_rejectsMissingToken() throws Exception {
        mockMvc.perform(post("/internal/meta/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void refresh_rejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/internal/meta/refresh").header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Configuration(proxyBeanMethods = false)
    static class WebConfiguration {

        @Bean
        InternalMetaController internalMetaController(LoadServiceMeta loadServiceMeta) {
            return new InternalMetaController(loadServiceMeta, INTERNAL_TOKEN);
        }
    }
}
