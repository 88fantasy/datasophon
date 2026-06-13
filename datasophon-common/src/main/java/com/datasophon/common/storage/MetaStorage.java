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
 * 元数据存储接口，用于管理大数据服务组件的元数据资源。
 * <p>
 * 支持 VOS DDL 和 K8s 两种存储类型，提供服务元数据的读取、保存、删除等操作。
 * 主要功能包括：
 * <ul>
 *   <li>列出指定类型的所有服务元数据</li>
 *   <li>获取服务的 DDL 文件内容</li>
 *   <li>获取 Helm values.yaml 配置文件</li>
 *   <li>下载服务相关资源文件</li>
 *   <li>将本地文件目录迁移到存储系统</li>
 *   <li>删除服务元数据</li>
 * </ul>
 * </p>
 *
 * @author zhanghuangbin
 */
public interface MetaStorage {
    
    /**
     * 物理机/虚拟机 类型标识
     */
    String PHYSICAL = "physical";
    
    /**
     * K8s 类型标识
     */
    String K8S = "k8s";
    
    /**
     * 检查当前存储是否启用
     * @return 如果存储已启用返回 true，否则返回 false
     */
    boolean isEnabled();
    
    /**
     * 列出指定类型的所有服务元数据项
     * @param type 服务类型（如 BARE_METAL 或 K8S）
     * @return 服务元数据项列表
     */
    List<ServiceMetaItem> listService(String type);
    
    /**
     * 获取服务的 DDL 文件内容
     * @param item 服务元数据项
     * @return DDL 文件内容（UTF-8 编码的字符串）
     * @throws FileNotFoundException 当 DDL 文件不存在时抛出
     * @throws IllegalStateException 当下载过程中发生其他异常时抛出
     */
    default String getServiceDdL(ServiceMetaItem item) throws FileNotFoundException {
        try {
            return getResourceAsString(item, PHYSICAL.equals(item.getType()) ? Constants.SERVICE_DDL : Constants.MANIFEST_DDL, true);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
    
    /**
     * 保存服务的 DDL 内容到存储系统
     * @param item 服务元数据项
     * @param content DDL 文件内容
     * @throws IOException 当写入失败时抛出
     */
    void saveServiceDdl(ServiceMetaItem item, String content) throws IOException;
    
    /**
     * 获取 Helm chart 的 values.yaml 配置文件内容
     * @param item 服务元数据项
     * @param chartName chart 名称
     * @return values.yaml 文件内容（UTF-8 编码的字符串）
     * @throws IOException 当读取失败时抛出
     */
    String getHelmValuesYaml(ServiceMetaItem item, String chartName) throws IOException;
    
    /**
     * 下载服务资源文件到输出流
     * @param item 服务元数据项
     * @param relativePath 相对路径
     * @param supplier 输出流提供者
     * @throws FileNotFoundException 当资源文件不存在时抛出
     * @throws IOException 当读取或写入失败时抛出
     */
    void downResource(ServiceMetaItem item, String relativePath, OutputStreamSupplier supplier) throws FileNotFoundException, IOException;
    
    /**
     * 将服务资源文件作为字符串读取
     * @param item 服务元数据项
     * @param relativePath 相对路径
     * @param required 是否必须存在，false 时文件不存在返回 null
     * @return 文件内容（UTF-8 编码的字符串），文件为空或不存在时返回 null
     * @throws IOException 当 required 为 true 且读取失败时抛出
     */
    default String getResourceAsString(ServiceMetaItem item, String relativePath, boolean required) throws IOException {
        if (relativePath == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            downResource(item, relativePath, () -> out);
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (required) {
                throw e;
            }
            return null;
        }
    }
    
    /**
     * 将目录文件迁移到存储系统
     * @param dir 要迁移的目录
     * @param includeDir 是否在路径中包含目录名
     * @throws IOException 当迁移失败时抛出
     */
    default void moveToStorage(File dir, boolean includeDir) throws IOException {
        moveToStorage(dir, relative -> {
            if (includeDir) {
                return dir.getName() + "/" + relative;
            }
            return relative;
        });
    }
    
    /**
     * 将目录文件迁移到存储系统，支持自定义路径处理
     * @param dir 要迁移的目录
     * @param relativePathHandler 相对路径处理器，用于转换或处理路径
     * @throws IOException 当迁移失败时抛出
     */
    void moveToStorage(File dir, Function<String, String> relativePathHandler) throws IOException;
    
    /**
     * 删除物理(VOS)服务的元数据
     * @param frameCode 框架代码
     * @param serviceName 服务名称
     */
    default void removePhysicalMeta(String frameCode, String serviceName) {
        removeMeta(frameCode, serviceName, PHYSICAL);
    }
    
    /**
     * 删除 K8s 服务的元数据
     * @param frameCode 框架代码
     * @param serviceName 服务名称
     */
    default void removeK8sMeta(String frameCode, String serviceName) {
        removeMeta(frameCode, serviceName, K8S);
    }
    
    /**
     * 删除指定类型的服务元数据
     * @param frameCode 框架代码
     * @param serviceName 服务名称
     * @param type 元数据类型（VOS_DDL 或 K8S）
     */
    void removeMeta(String frameCode, String serviceName, String type);
    
    /**
     * 输出流提供者函数式接口
     */
    interface OutputStreamSupplier {
        OutputStream get() throws IOException;
    }
}
