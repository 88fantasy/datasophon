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

package com.datasophon.api.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterVariable;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OtelCredentialServiceTest {
    
    @Test
    void generatesDistinctClusterCredentialsAndPersistsThem() {
        ClusterVariableService variables = mock(ClusterVariableService.class);
        when(variables.getVariableByVariableName(7, "OTELCOLLECTOR", "dorisCollectorPassword"))
                .thenReturn(null);
        when(variables.getVariableByVariableName(7, "OTELCOLLECTOR", "dorisReaderPassword"))
                .thenReturn(null);
        OtelCredentialService service = new OtelCredentialService(variables);
        
        OtelCredentials credentials = service.getOrCreate(7);
        
        assertThat(credentials.collectorPassword()).hasSize(32);
        assertThat(credentials.readerPassword()).hasSize(32).isNotEqualTo(credentials.collectorPassword());
        ArgumentCaptor<ClusterVariable> saved = ArgumentCaptor.forClass(ClusterVariable.class);
        org.mockito.Mockito.verify(variables, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ClusterVariable::getClusterId).containsOnly(7);
        assertThat(saved.getAllValues()).extracting(ClusterVariable::getVariableValue)
                .doesNotContain("CHANGE_ME_AT_A3");
    }
    
    @Test
    void reusesPersistedCredentials() {
        ClusterVariableService variables = mock(ClusterVariableService.class);
        when(variables.getVariableByVariableName(7, "OTELCOLLECTOR", "dorisCollectorPassword"))
                .thenReturn(variable("collector-secret"));
        when(variables.getVariableByVariableName(7, "OTELCOLLECTOR", "dorisReaderPassword"))
                .thenReturn(variable("reader-secret"));
        
        OtelCredentials credentials = new OtelCredentialService(variables).getOrCreate(7);
        
        assertThat(credentials).isEqualTo(new OtelCredentials("collector-secret", "reader-secret"));
        org.mockito.Mockito.verify(variables, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
    
    private static ClusterVariable variable(String value) {
        ClusterVariable variable = new ClusterVariable();
        variable.setVariableValue(value);
        return variable;
    }
}
