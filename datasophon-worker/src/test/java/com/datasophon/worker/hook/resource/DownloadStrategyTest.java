package com.datasophon.worker.hook.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.hook.HookContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class DownloadStrategyTest {
    
    private static final byte[] RESOURCE_CONTENT = "resource-content".getBytes(StandardCharsets.UTF_8);
    
    static {
        URL url = DownloadStrategyTest.class.getClassLoader().getResource("common-test.properties");
        if (url != null) {
            System.setProperty("commonPropertiesLocation", url.getPath());
        }
    }
    
    @TempDir
    Path tempDir;
    
    @Test
    void skipsDownloadWhenTargetMd5Matches() throws IOException {
        Path target = tempDir.resolve("control.sh");
        Files.write(target, RESOURCE_CONTENT);
        HookContext context = newContext("control.sh", FileUtils.md5(target.toFile()));
        DownloadStrategy strategy = new DownloadStrategy();
        
        try (
                MockedStatic<PkgInstallPathUtils> installPath = mockStatic(PkgInstallPathUtils.class);
                MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class);
                MockedStatic<ShellUtils> shellUtils = mockStatic(ShellUtils.class)) {
            installPath.when(() -> PkgInstallPathUtils.getInstallHome(context)).thenReturn(tempDir.toString());
            
            ExecResult result = strategy.invoke(context);
            
            assertThat(result.isSuccess()).isTrue();
            storageUtils.verifyNoInteractions();
            shellUtils.verifyNoInteractions();
        }
    }
    
    @Test
    void downloadsPhysicalMetaResourceDirectlyFromNexus() throws Exception {
        Path target = tempDir.resolve("script/control.sh");
        HookContext context = newContext("script/control.sh", md5Of(RESOURCE_CONTENT));
        MetaStorage metaStorage = mock(MetaStorage.class);
        doAnswer(invocation -> {
            MetaStorage.OutputStreamSupplier supplier = invocation.getArgument(2);
            try (OutputStream out = supplier.get()) {
                out.write(RESOURCE_CONTENT);
            }
            return null;
        }).when(metaStorage).downResource(any(ServiceMetaItem.class), eq("script/control.sh"), any());
        
        try (
                MockedStatic<PkgInstallPathUtils> installPath = mockStatic(PkgInstallPathUtils.class);
                MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class);
                MockedStatic<ShellUtils> shellUtils = mockStatic(ShellUtils.class)) {
            installPath.when(() -> PkgInstallPathUtils.getInstallHome(context)).thenReturn(tempDir.toString());
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);
            shellUtils.when(() -> ShellUtils.execShell("chmod 755 " + target)).thenReturn(ExecResult.success());
            
            ExecResult result = new DownloadStrategy().invoke(context);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readAllBytes(target)).isEqualTo(RESOURCE_CONTENT);
            ArgumentCaptor<ServiceMetaItem> itemCaptor = ArgumentCaptor.forClass(ServiceMetaItem.class);
            verify(metaStorage).downResource(itemCaptor.capture(), eq("script/control.sh"), any());
            assertThat(itemCaptor.getValue())
                    .extracting(ServiceMetaItem::getFramework, ServiceMetaItem::getServiceName, ServiceMetaItem::getType)
                    .containsExactly("DATASOPHON-3.0", "PROMTAIL", MetaStorage.PHYSICAL);
        }
    }
    
    @Test
    void rejectsMismatchedMd5WithoutReplacingExistingTarget() throws Exception {
        byte[] oldContent = "old-content".getBytes(StandardCharsets.UTF_8);
        Path target = tempDir.resolve("control.sh");
        Files.write(target, oldContent);
        HookContext context = newContext("control.sh", md5Of("expected-content".getBytes(StandardCharsets.UTF_8)));
        MetaStorage metaStorage = mock(MetaStorage.class);
        doAnswer(invocation -> {
            MetaStorage.OutputStreamSupplier supplier = invocation.getArgument(2);
            try (OutputStream out = supplier.get()) {
                out.write(RESOURCE_CONTENT);
            }
            return null;
        }).when(metaStorage).downResource(any(ServiceMetaItem.class), eq("script/control.sh"), any());
        
        try (
                MockedStatic<PkgInstallPathUtils> installPath = mockStatic(PkgInstallPathUtils.class);
                MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class);
                MockedStatic<ShellUtils> shellUtils = mockStatic(ShellUtils.class)) {
            installPath.when(() -> PkgInstallPathUtils.getInstallHome(context)).thenReturn(tempDir.toString());
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);
            
            ExecResult result = new DownloadStrategy().invoke(context);
            
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getExecOut()).contains("MD5");
            assertThat(Files.readAllBytes(target)).isEqualTo(oldContent);
            assertNoTemporaryFiles();
            shellUtils.verifyNoInteractions();
        }
    }
    
    @Test
    void returnsFailureAndCleansTemporaryFileWhenNexusDownloadFails() throws Exception {
        Path target = tempDir.resolve("control.sh");
        HookContext context = newContext("control.sh", md5Of(RESOURCE_CONTENT));
        MetaStorage metaStorage = mock(MetaStorage.class);
        doThrow(new IOException("nexus unavailable"))
                .when(metaStorage)
                .downResource(any(ServiceMetaItem.class), eq("script/control.sh"), any());
        
        try (
                MockedStatic<PkgInstallPathUtils> installPath = mockStatic(PkgInstallPathUtils.class);
                MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class);
                MockedStatic<ShellUtils> shellUtils = mockStatic(ShellUtils.class)) {
            installPath.when(() -> PkgInstallPathUtils.getInstallHome(context)).thenReturn(tempDir.toString());
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);
            
            ExecResult result = new DownloadStrategy().invoke(context);
            
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getExecOut()).contains("nexus unavailable");
            assertThat(target).doesNotExist();
            assertNoTemporaryFiles();
            shellUtils.verifyNoInteractions();
        }
    }
    
    private HookContext newContext(String to, String md5) {
        HookContext context = new HookContext();
        context.setServiceName("PROMTAIL");
        context.setServiceRoleName("Promtail");
        context.setParams(Map.of("from", "script/control.sh", "to", to, "md5", md5));
        context.setGlobalVariables(Map.of("${__frameCode__}", "DATASOPHON-3.0"));
        return context;
    }
    
    private String md5Of(byte[] content) throws IOException {
        Path file = Files.createTempFile(tempDir, "md5-", ".bin");
        Files.write(file, content);
        return FileUtils.md5(file.toFile());
    }
    
    private void assertNoTemporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files.map(Path::getFileName).map(Path::toString))
                    .noneMatch(name -> name.endsWith(".tmp"));
        }
    }
}
