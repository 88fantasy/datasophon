package com.datasophon.worker.hook.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.hook.HookContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class AppendLineStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    void repeatedHooksAtSameLineRemainIdempotent() throws Exception {
        Path target = tempDir.resolve("bin/start.sh");
        Files.createDirectories(target.getParent());
        Files.write(target, List.of("#!/bin/bash", "exec app"));
        HookContext first = context("export JAVA_HOME=/opt/jdk");
        HookContext second = context("export OTEL_JAVAAGENT=/opt/otel-agent.jar");
        AppendLineStrategy strategy = new AppendLineStrategy();

        try (MockedStatic<PkgInstallPathUtils> paths = mockStatic(PkgInstallPathUtils.class)) {
            paths.when(() -> PkgInstallPathUtils.getInstallHome(first)).thenReturn(tempDir.toString());
            paths.when(() -> PkgInstallPathUtils.getInstallHome(second)).thenReturn(tempDir.toString());
            strategy.invoke(first);
            strategy.invoke(second);
            strategy.invoke(first);
            strategy.invoke(second);
        }

        List<String> lines = Files.readAllLines(target);
        assertThat(lines).containsOnlyOnce("export JAVA_HOME=/opt/jdk");
        assertThat(lines).containsOnlyOnce("export OTEL_JAVAAGENT=/opt/otel-agent.jar");
    }

    private static HookContext context(String text) {
        HookContext context = new HookContext();
        context.setServiceName("NACOS");
        context.setServiceRoleName("NacosServer");
        context.setParams(Map.of("source", "bin/start.sh", "line", 2, "text", text));
        return context;
    }
}
