package com.datasophon.common.storage;

import com.datasophon.common.Constants;
import com.datasophon.common.storage.vo.ServiceMetaItem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 */
public interface MetaStorage {

    String VOS_DDL = "vos_ddl";

    String K8S = "k8s";

    boolean isEnabled();

    List<ServiceMetaItem> listService(String type);

    default String getServiceDdL(ServiceMetaItem item) throws FileNotFoundException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            downResource(item, VOS_DDL.equals(item.getType()) ? Constants.SERVICE_DDL : Constants.MANIFEST_DDL, () -> out);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    void saveServiceDdl(ServiceMetaItem item, String content) throws IOException;

    void downResource(ServiceMetaItem item, String relativePath, OutputStreamSupplier supplier) throws FileNotFoundException, IOException;

    default void moveToStorage(File dir, boolean includeDir) throws IOException {
        moveToStorage(dir, relative -> {
            if (includeDir) {
                return dir.getName() + "/" + relative;
            }
            return relative;
        });
    }


    void moveToStorage(File dir, Function<String, String> relativePathHandler) throws IOException;

    default void removeVosMeta(String frameCode, String serviceName) {
        removeMeta(frameCode, serviceName, VOS_DDL);
    }

    default void removeK8sMeta(String frameCode, String serviceName) {
        removeMeta(frameCode, serviceName, K8S);
    }

    void removeMeta(String frameCode, String serviceName, String type);

    interface OutputStreamSupplier {
        OutputStream get() throws IOException;
    }
}
