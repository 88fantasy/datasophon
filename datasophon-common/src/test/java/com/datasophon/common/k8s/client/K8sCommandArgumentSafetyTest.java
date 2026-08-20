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

package com.datasophon.common.k8s.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class K8sCommandArgumentSafetyTest {

    private static String originalPropertiesLocation;

    @BeforeAll
    static void configureProperties() {
        originalPropertiesLocation = System.getProperty("commonPropertiesLocation");
        System.setProperty("commonPropertiesLocation",
                new File("src/test/resources/common.properties").getAbsolutePath());
    }

    @AfterAll
    static void restoreProperties() {
        if (originalPropertiesLocation == null) {
            System.clearProperty("commonPropertiesLocation");
        } else {
            System.setProperty("commonPropertiesLocation", originalPropertiesLocation);
        }
    }

    @Test
    void helmKeepsUntrustedReleaseAsOneProcessArgument() {
        String untrustedRelease = "release; touch /tmp/helm-injected";
        try (MockedStatic<ShellUtils> shell = Mockito.mockStatic(ShellUtils.class)) {
            ExecResult result = successfulResult(null);
            shell.when(() -> ShellUtils.exec(any(), any(), anyLong()))
                    .thenAnswer(invocation -> {
                        List<String> args = invocation.getArgument(1);
                        if (args.contains("status")) {
                            org.junit.jupiter.api.Assertions.assertTrue(args.contains(untrustedRelease));
                            org.junit.jupiter.api.Assertions.assertFalse(args.contains("bash"));
                            org.junit.jupiter.api.Assertions.assertFalse(args.contains("-c"));
                            org.junit.jupiter.api.Assertions.assertTrue(args.contains("--kubeconfig"));
                            org.junit.jupiter.api.Assertions.assertFalse(args.contains("test-token"));
                        }
                        return result;
                    });
            ClientOptions options = tokenOptions();

            new HelmClient(options).execute(Arrays.asList("status", untrustedRelease), 30);

            shell.verify(() -> ShellUtils.execWithBash(any(), any(), anyLong()), never());
        }
    }

    @Test
    void kubectlKeepsUntrustedNamespaceAsOneProcessArgument() {
        String untrustedNamespace = "default; touch /tmp/kubectl-injected";
        try (MockedStatic<ShellUtils> shell = Mockito.mockStatic(ShellUtils.class)) {
            ExecResult result = successfulResult("{\"items\":[]}");
            shell.when(() -> ShellUtils.exec(any(), any(), anyLong()))
                    .thenAnswer(invocation -> {
                        List<String> args = invocation.getArgument(1);
                        if (args.contains("pods")) {
                            org.junit.jupiter.api.Assertions.assertTrue(args.contains(untrustedNamespace));
                            org.junit.jupiter.api.Assertions.assertFalse(args.contains("bash"));
                            org.junit.jupiter.api.Assertions.assertFalse(args.contains("-c"));
                            org.junit.jupiter.api.Assertions.assertTrue(args.contains("--kubeconfig"));
                            org.junit.jupiter.api.Assertions.assertFalse(args.contains("test-token"));
                        }
                        return result;
                    });
            ClientOptions options = tokenOptions();

            new KubectlClient(options).executeToJson(
                    Arrays.asList("get", "pods", "-n", untrustedNamespace), 30);

            shell.verify(() -> ShellUtils.execWithBash(any(), any(), anyLong()), never());
        }
    }

    private static ClientOptions tokenOptions() {
        ClientOptions options = new ClientOptions();
        options.setToken("test-token");
        options.setServerName("https://k8s.example.com");
        return options;
    }

    private static ExecResult successfulResult(String output) {
        ExecResult result = mock(ExecResult.class);
        when(result.isSuccess()).thenReturn(true);
        when(result.getExecOut()).thenReturn(output);
        return result;
    }
}
