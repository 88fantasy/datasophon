package com.datasophon.cli.init;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.RepositoriesType;
import com.datasophon.common.model.uni.request.*;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "registry", description = "init registry")
public class InitRegistry extends InitBase {

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;

    @CommandLine.Option(names = {"-t", "--type"}, description = "制品类型", required = false)
    String type = "nexus";

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-in", "installPath"}, description = "安装路径", required = true)
    String installPath;

    @CommandLine.Option(names = {"-x", "--x86Tar"}, description = "x86_64包", required = true)
    String x86Tar;

    @CommandLine.Option(names = {"-a", "--aarch64Tar"}, description = "aarch64包", required = true)
    String aarch64Tar;

    @CommandLine.Option(names = {"-wh", "--webHost"}, description = "webHost", required = true)
    String webHost;

    @CommandLine.Option(names = {"-wp", "--webPort"}, description = "webPort", required = true)
    String webPort;

    @CommandLine.Option(names = {"-u", "--username"}, description = "username", required = true)
    String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "password", required = true)
    String password;

    @CommandLine.Option(names = {"-r", "--repositories"}, description = "repositories", split = ",", required = true)
    List<String> repositories;

    @CommandLine.Option(names = {"-dp", "--dockerHttpPort"}, description = "http端口", required = true)
    Integer dockerHttpPort;

    private String DISCLAIMER = "Use of Sonatype Nexus Repository - Community Edition is governed by the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula. By returning the value from ‘accepted:false’ to ‘accepted:true’, you acknowledge that you have read and agree to the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula.";

    @Override
    public String name() {
        return "安装制品库registry";
    }

    @Override
    public boolean doRun(Executor executor) {
        if(!enableRegistry) {
            log.info("registry enable is: {}, skip", enableRegistry);
            return true;
        }

        if(!executor.exists(installPath).getExecResult()) {
            executor.execShell(String.format("mkdir -p %s", installPath));
        }
        String home = String.format("%s/nexusDir", installPath);
        String nexusPath = String.format("%s/nexus", home);
        String nexusPropertiesPath = String.format("%s/etc/nexus-default.properties", nexusPath);
        String sonatypePath = String.format("%s/sonatype-work", home);
        String passwordPath = String.format("%s/nexus3/admin.password", sonatypePath);
        String baseUrl = String.format("http://%s:%s", webHost, webPort);
        String tarPath = String.format("%s/%s", packagePath, x86Tar);
        if (ArchType.AARCH64 == executor.getArch()) {
            tarPath = String.format("%s/%s", packagePath, aarch64Tar);
        }
        if (!executor.exists(tarPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + tarPath);
        }

        if(executor.exists(nexusPath).getExecResult()) {
            log.info("nexusDir path exist: {}", nexusPath);
        } else {
            executor.execShell(String.format("mkdir -p %s", home));
            executor.execShell(String.format("tar xzf %s -C %s", tarPath, home));
            // nexus-3.85.0-03
            executor.execShell(String.format("mv %s/nexus-* %s", home, nexusPath));
            nexusPropertiesGenerate(executor, nexusPropertiesPath, webPort);
        }

        //检测启动
        if(!checkStart(executor)) {
            start(executor, nexusPath);
            boolean flag = true;
            int time = 20;
            while (flag && time > 0) {
                boolean passwordExist = executor.exists(passwordPath).getExecResult();
                if(!passwordExist) {
                    log.info("nexus {} not exist. Maybe password init...", passwordPath);
                    executor.execShell("sleep 10");
                    time = time - 1;
                } else {
                    flag = false;
                }
            }
        }

        //初始化
        if(checkStart(executor)) {
            if(executor.exists(passwordPath).getExecResult()) {
                String oldPassword = executor.getFileString(passwordPath).getExecOut();
                changePassword(baseUrl, oldPassword);
            } else {
                log.warn("admin.password[{}] is not exist", passwordPath);
            }
            systemEula(baseUrl);
            repoCreateByList(baseUrl, repositories);
            log.info("nexus install sucess. path:{}", home);
            return true;
        } else {
            throw new RuntimeException(String.format("nexus install failed. path:%s", home));
        }
    }

    public boolean checkStart(Executor executor) {
        ExecResult execResult = executor.execShell("ps -ef | grep nexus | grep sonatype-work | grep -v datasophon-cli | grep -v grep");
        if(execResult.getExecResult()) {
            log.info("nexus has started.");
            return true;
        } else {
            log.info("nexus has not started.");
            return false;
        }
    }

    public boolean start(Executor executor, String nexusInstallPath) {
        ExecResult execResult = executor.execShell(String.format("%s/bin/nexus start", nexusInstallPath));
        return execResult.getExecResult();
    }

    public static void nexusPropertiesGenerate(Executor executor, String path, String webPort) {
        List<String> conf = new ArrayList<>();
        conf.add(String.format("application-port=%s", webPort));
        conf.add("application-host=0.0.0.0");
        conf.add("nexus-args=${jetty.etc}/jetty.xml,${jetty.etc}/jetty-http.xml,${jetty.etc}/jetty-requestlog.xml");
        conf.add("nexus-context-path=/");
        executor.writeLines(conf, path);
        log.info("nexus-default.properties path:{} generate finish", path);
    }

    public boolean changePassword(String baseUrl, String oldPassword) {
        String url = String.format("%s/service/rest/v1/security/users/admin/change-password", baseUrl);
        log.info("init password,url:{}", url);
        try (HttpResponse response = HttpRequest.put(url)
                    .basicAuth(username, oldPassword)
                    .body(password)
                    .contentType("text/plain")
                    .timeout(30000)
                    .execute()){
            if (response.getStatus() == 204) {
                log.info("修改密码成功");
            } else {
                log.error("修改密码失败. url:{}, status:{}, body:{}", url, response.getStatus(), response.body());
                return false;
            }
        }
        return true;
    }

    public boolean systemEula(String baseUrl) {
        String url = String.format("%s/service/rest/v1/system/eula", baseUrl);
        log.info("init eula协议 ,url:{}", url);
        Eula eula = new Eula();
        eula.setAccepted(true);
        eula.setDisclaimer(DISCLAIMER);

        try (HttpResponse response = HttpRequest.post(url)
                .basicAuth(username, password)
                .body(JSONObject.toJSONString(eula))
                .contentType("application/json")
                .timeout(30000)
                .execute()) {
            if (response.getStatus() == 204) {
                log.info("eula协议设置成功");
            } else {
                log.error("eula协议设置失败. url:{}, response.status:{}, response.body:{}", url, response.getStatus(), response.body());
                return false;
            }
        }
        return true;
    }

    public void repoCreateByList(String baseUrl, List<String> repos) {
        log.info("nexus init repositories");
        for(String repo : repos) {
            RepositoriesType repositoriesType = RepositoriesType.of(repo);
            switch (repositoriesType) {
                case APT:
                    aptRepoCreate(baseUrl, repo, "jammy");
                    break;
                case RAW:
                    rawRepoCreate(baseUrl, repo);
                    break;
                case YUM:
                    yumRepoCreate(baseUrl, repo);
                    break;
                case DOCKER:
                    dockerCreate(baseUrl, repo);
                case HELM:
                    helmCreate(baseUrl, repo);
                default:

            }
        }
    }

    public boolean yumRepoCreate(String baseUrl, String repoName) {
        String url = String.format("%s/service/rest/v1/repositories/yum/hosted", baseUrl);
        YumRepository repositoryReq = new YumRepository();
        repositoryReq.setName(repoName);

        return HttpPost(url, JSONObject.toJSONString(repositoryReq), repoName);
    }

    public boolean aptRepoCreate(String baseUrl, String repoName, String distribution) {
        String url = String.format("%s/service/rest/v1/repositories/apt/hosted", baseUrl);
        AptRepository repositoryReq = new AptRepository();
        repositoryReq.setName(repoName);

        AptRepository.Apt apt = new AptRepository.Apt();
        apt.setDistribution(distribution);
        repositoryReq.setApt(apt);

        AptRepository.AptSigning aptSigning = new AptRepository.AptSigning();
        aptSigning.setKeypair("key");
        aptSigning.setPassphrase("");
        repositoryReq.setAptSigning(aptSigning);

        return HttpPost(url, JSONObject.toJSONString(repositoryReq), repoName);
    }

    public boolean rawRepoCreate(String baseUrl, String repoName) {
        String url = String.format("%s/service/rest/v1/repositories/raw/hosted", baseUrl);
        RawRepository repositoryReq = new RawRepository();
        repositoryReq.setName(repoName);

        return HttpPost(url, JSONObject.toJSONString(repositoryReq), repoName);
    }

    public boolean helmCreate(String baseUrl, String repoName) {
        String url = String.format("%s/service/rest/v1/repositories/helm/hosted", baseUrl);
        HelmRepository repositoryReq = new HelmRepository();
        repositoryReq.setName(repoName);
        return HttpPost(url, JSONObject.toJSONString(repositoryReq), repoName);
    }

    public boolean dockerCreate(String baseUrl, String repoName) {
        String url = String.format("%s/service/rest/v1/repositories/docker/hosted", baseUrl);
        DockerRepository repositoryReq = new DockerRepository();
        repositoryReq.setName(repoName);
        repositoryReq.getDocker().setHttpsPort(dockerHttpPort);
        return HttpPost(url, JSONObject.toJSONString(repositoryReq), repoName);
    }

    /**
     *
     * @param url
     * @param body
     * @return
     * 201
     * Repository created
     *
     * 401
     * Authentication required
     *
     * 403
     * Insufficient permissions
     *
     * 405
     * Feature is disabled in High Availability
     */
    private boolean HttpPost(String url, String body, String repoName) {
        log.info("请求url:{}, body:{}", url, body);
        try (HttpResponse response = HttpRequest.post(url)
                .basicAuth(username, password)
                .body(body)
                .contentType("application/json")
                .timeout(30000)
                .execute()) {
            if (response.getStatus() == 201) {
                log.info("创建{}成功", repoName);
            } else {
                log.error("创建{}失败. url:{}, response.status:{}, response.body:{}", repoName, url, response.getStatus(), response.body());
                return false;
            }
        }
        return true;
    }


}
