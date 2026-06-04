package com.datasophon.k8sagent.deploy;

import com.datasophon.common.k8s.client.DockerClientWrapper;
import com.datasophon.common.k8s.client.DockerClientWrapperImpl;
import com.datasophon.common.k8s.config.DockerRegistryOptions;
import com.datasophon.common.k8s.vo.docker.LoadImageResult;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.client.HelmRepoClient;
import com.datasophon.common.utils.nexus.vo.ExecResult;
import com.datasophon.k8sagent.PropertiesPathUtils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import cn.hutool.core.io.FileUtil;

/**
 * 将 k8s-agent 的 helm chart 和 docker 镜像推送到 nexus 仓库
 *
 * @author zhanghuangbin
 */
public class UploadK8sAgentTest {
    
    private static final String VERSION = "3.0-SNAPSHOT";
    
    @BeforeAll
    public static void init() {
        PropertiesPathUtils.resetPropertyFile();
    }
    
    /**
     * 扫描 target 下所有 tgz 文件，判断是否为 helm chart（包含 Chart.yaml），
     * 从 Chart.yaml 中读取 name 和 version，以 $chartName-$chartVersion.tgz 为文件名上传到 nexus helm 仓库
     */
    @Test
    public void uploadHelmChart() throws IOException {
        File tmp = PathUtils.getTmpDir("deploy");
        try {
            File base = new File("./target");
            System.out.println("target目录: " + base.getAbsolutePath());
            
            File[] tgzFiles = base.listFiles((dir, name) -> name.endsWith(".tgz"));
            if (tgzFiles == null || tgzFiles.length == 0) {
                throw new IllegalStateException("target 目录下没有找到 tgz 文件");
            }
            
            HelmRepoClient helmClient = NexusFacade.getHelmClient();
            
            for (File tgzFile : tgzFiles) {
                Map<String, Object> chartYaml = extractChartYaml(tgzFile);
                if (chartYaml == null) {
                    System.out.println("跳过非 helm chart 文件: " + tgzFile.getName());
                    continue;
                }
                
                String chartName = (String) chartYaml.get("name");
                String chartVersion = (String) chartYaml.get("version");
                if (chartName == null || chartVersion == null) {
                    System.out.println("Chart.yaml 缺少 name 或 version，跳过: " + tgzFile.getName());
                    continue;
                }
                
                String uploadFileName = chartName + "-" + chartVersion + ".tgz";
                System.out.println("发现 helm chart: " + tgzFile.getName() + " -> 上传为: " + uploadFileName);
                
                // 如果原始文件名与目标文件名不同，拷贝一份以目标文件名命名的临时文件
                File uploadFile;
                if (tgzFile.getName().equals(uploadFileName)) {
                    uploadFile = tgzFile;
                } else {
                    uploadFile = new File(tmp, uploadFileName);
                    Files.copy(tgzFile.toPath(), uploadFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                ExecResult result = helmClient.uploadChartToHelmRepo(uploadFile);
                System.out.println("上传 helm chart 结果: success=" + result.isSuccess() + ", message=" + result.getMessage());
            }
        } finally {
            FileUtil.del(tmp);
        }
        
    }
    
    /**
     * 从 tgz 文件中提取 Chart.yaml 的内容，如果不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractChartYaml(File tgzFile) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(tgzFile);
                GZIPInputStream gis = new GZIPInputStream(fis);
                TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {
            
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                // Chart.yaml 通常在第一级目录下，如 datasophon-k8s-agent/Chart.yaml
                String entryName = entry.getName();
                if (entry.isFile() && (entryName.equals("Chart.yaml") || entryName.endsWith("/Chart.yaml"))) {
                    // 确保是直接子目录下的 Chart.yaml（不是嵌套更深的）
                    String[] parts = entryName.split("/");
                    if (parts.length <= 2) {
                        Yaml yaml = new Yaml();
                        return yaml.load(tis);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 将 docker 镜像推送到 nexus docker 仓库
     */
    @Test
    public void uploadDockerImage() throws IOException {
        File base = new File("./target");
        System.out.println("target目录: " + base.getAbsolutePath());
        
        File[] imageFiles = base.listFiles((dir, name) -> name.contains("-image-"));
        if (imageFiles == null || imageFiles.length == 0) {
            throw new IllegalStateException("target 目录下没有找到包含 -image- 的镜像文件");
        }
        
        NexusUri uri = NexusFacade.getNexusUri();
        DockerRegistryOptions options = new DockerRegistryOptions();
        options.setInsecure(true);
        options.setHost(uri.getIp());
        options.setPort(uri.getPort());
        options.setUsername(uri.getUser());
        options.setPassword(uri.getPassword());
        options.setRepo("docker");
        
        DockerClientWrapper client = new DockerClientWrapperImpl(options);
        
        // 登录 docker registry
        client.login();
        System.out.println("docker registry 登录成功");
        
        List<LoadImageResult> allImages = new java.util.ArrayList<>();
        for (File imageFile : imageFiles) {
            System.out.println("加载镜像文件: " + imageFile.getName());
            List<LoadImageResult> images = client.load(imageFile);
            System.out.println("加载镜像完成, 共 " + images.size() + " 个镜像");
            allImages.addAll(images);
        }
        
        // 推送每个镜像
        for (LoadImageResult image : allImages) {
            System.out.println("推送镜像: " + image.getNewQualifierImage());
            client.push(image.getNewQualifierImage());
        }
        
        // 按原始镜像分组，创建并推送 manifest
        Map<String, List<LoadImageResult>> grouped = allImages.stream()
                .collect(Collectors.groupingBy(LoadImageResult::getOldQualifierImage));
        for (Map.Entry<String, List<LoadImageResult>> entry : grouped.entrySet()) {
            System.out.println("创建并推送 manifest: " + entry.getKey());
            client.createManifest(entry.getKey(), entry.getValue());
        }
        
        System.out.println("docker 镜像推送完成");
    }
}
