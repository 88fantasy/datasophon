package com.datasophon.api.service.extrepo.impl;

import akka.actor.ActorRef;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.LoadServiceMeta;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.SubmitTaskNodeActor;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.ClusterServiceCommandHostService;
import com.datasophon.api.service.ClusterServiceCommandService;
import com.datasophon.api.service.ClusterServiceInstanceConfigService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.service.extrepo.ExtRepoMetaService;
import com.datasophon.api.service.extrepo.ctx.DeploymentDAGBuildContext;
import com.datasophon.api.service.extrepo.ctx.ExecDAGBuilderContext;
import com.datasophon.api.service.extrepo.ctx.MetaParseOption;
import com.datasophon.api.service.extrepo.ctx.SrvDependenciesContext;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.extrepo.utils.PathUtils;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.strategy.ServiceRoleStrategyContext;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.vo.extrepo.DeploymentDAG;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.SubmitActiveTaskNodeCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.DAG;
import com.datasophon.common.model.DAGGraph;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ZipUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.ClusterServiceCommandHostEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.dao.model.extrepo.DeploySrvConfig;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import com.datasophon.dao.model.extrepo.ExtRepoMetaFsModel;
import com.datasophon.dao.model.extrepo.FrameworkMeta;
import com.datasophon.dao.model.extrepo.ServiceMeta;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Service("extRepoMetaService")
public class ExtRepoMetaServiceImpl implements ExtRepoMetaService {

    private static final Logger log = LoggerFactory.getLogger(ExtRepoMetaServiceImpl.class);

    private final Map<Integer, ImportCompProgressVO> importCmpMap = new ConcurrentHashMap<>();


    @Autowired
    private UploadTempFileService uploadTempFileService;

    @Autowired
    private FrameInfoService frameInfoService;

    @Autowired
    private LoadServiceMeta loadServiceMeta;

    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private FrameServiceService frameService;

    @Autowired
    private FrameServiceService frameServiceService;

    @Autowired
    private ServiceInstallService serviceInstallService;


    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;

    @Autowired
    private FrameServiceRoleService frameServiceRoleService;

    @Autowired
    private ClusterServiceCommandHostService commandHostService;

    @Autowired
    private ClusterServiceCommandHostCommandService hostCommandService;


    @Autowired
    private ClusterServiceCommandService commandService;

    @Autowired
    private ClusterHostService hostService;

    @Autowired
    private ClusterServiceInstanceService serviceInstanceService;

    @Autowired
    private ClusterServiceInstanceConfigService serviceInstanceConfigService;

    @Autowired
    private ClusterServiceRoleInstanceService roleInstanceService;


    @Override
    public ValidateResultVO validMetaFile(InstallComponentDTO dto) {
        return unzipMetaFile(dto, unzipDir -> {
            MetaParseOption option = new MetaParseOption();
            option.setRoot(unzipDir);
            ExtRepoMetaFsModel model = MetaUtils.parseRepoMeta(option);


            SrvDependenciesContext ctx = new SrvDependenciesContext();
            List<String> frames = model.getFrameworks().stream().map(FrameworkMeta::getFrameCode).collect(Collectors.toList());
            List<FrameServiceEntity> installedSrv = frameServiceService.listSimpleService(frames);
            ctx.addService(installedSrv);
            model.getFrameworks().stream().flatMap(f -> f.getServices().stream()).forEach(srv -> ctx.addService(srv.getFrameCode(), srv.getName()));

            List<String> errors = new ArrayList<>();
            model.getFrameworks().stream().flatMap(f -> f.getServices().stream()).forEach(srv -> {
                errors.addAll(ctx.validDependency(srv));
            });

            return new ValidateResultVO(errors);
        });
    }


    private <T> T unzipMetaFile(InstallComponentDTO dto, Function<String, T> mapper) {
        File metaFile = uploadTempFileService.getTempFile(dto.getMeteFileId());
        if (metaFile == null) {
            throw new BusinessException("元信息文件不存在");
        }
        String unzipDir = null;
        try {
            unzipDir = ZipUtils.unzipToTemp(metaFile.getAbsolutePath(), dto.getUnzipPasswd());
            MetaUtils.decodeMatchedFiles(unzipDir, dto.getContentDecodePasswd());

            return mapper.apply(unzipDir);
        } catch (IOException e) {
            throw new BusinessException("IO异常" + e.getMessage(), e);
        } finally {
            if (unzipDir != null) {
//                meta文件中包含了很大敏感信息，必须保证删除掉
                FileUtil.del(new File(unzipDir));
            }
        }
    }


    @Override
    public ValidateResultVO validatePkgFile(InstallComponentDTO dto) {
        ExtRepoMetaFsModel model = unzipMetaFile(dto, unzipDir -> {
            MetaParseOption option = new MetaParseOption();
            option.setRoot(unzipDir);
            return MetaUtils.parseRepoMeta(option);
        });

        File pkgFile = uploadTempFileService.getTempFile(dto.getPkgFileId());
        try {
            List<String> fileNames = ZipUtils.getZipEntry(pkgFile.getAbsolutePath(), null);
            List<String> errors = new ArrayList<>();
            model.getFrameworks().stream().flatMap(f -> f.getServices().stream()).forEach(srv -> {
                String filePath = PathUtils.unixStyle(MetaUtils.getFileRelativePath(srv));
                if (!fileNames.contains(filePath)) {
                    errors.add(filePath);
                }
                String md5Path = PathUtils.unixStyle(MetaUtils.getMd5FileRelativePath(srv));
                if (!fileNames.contains(md5Path)) {
                    errors.add(md5Path);
                }
            });

            return new ValidateResultVO(errors.stream().map(e -> String.format("压缩包中缺少%s文件", e)).collect(Collectors.toList()));
        } catch (Exception e) {
            throw new BusinessException("IO异常" + e.getMessage(), e);
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportCompProgressVO importCmp(InstallComponentDTO dto) {
        File metaFile = uploadTempFileService.getTempFile(dto.getMeteFileId());
        if (metaFile == null) {
            throw new BusinessException("元信息文件不存在");
        }
        File pkg = uploadTempFileService.getTempFile(dto.getPkgFileId());
        if (pkg == null) {
            throw new BusinessException("安装包文件不存在");
        }

        int progressId = RandomUtil.randomInt(1, Integer.MAX_VALUE);
        ImportCompProgressVO progress = new ImportCompProgressVO(progressId);
        importCmpMap.put(progressId, progress);

        CompletableFuture.runAsync(() -> doImportCmp(dto, progress));
        return progress;
    }


    private void doImportCmp(InstallComponentDTO dto, ImportCompProgressVO progress) {
        log.info("【导入第三方软件源】 进度ID:{}, 线程：{}, 开始执行", progress.getProgressId(), Thread.currentThread().getName());
        String error = null;
        String metaUnzipPath = null;
        String pkgUnzipPath = null;
        try {
            log.info("【导入第三方软件源】 进度ID:{}，开始解析meta数据", progress.getProgressId());
//            解析元数据
            File metaFile = uploadTempFileService.getTempFile(dto.getMeteFileId());
            metaUnzipPath = unpackMetaFile(metaFile, dto, progress);

            MetaParseOption option = new MetaParseOption();
            option.setRoot(metaUnzipPath);
            ExtRepoMetaFsModel vo = MetaUtils.parseRepoMeta(option);
            progress.setStep(9);
            log.info("【导入第三方软件源】 进度ID:{}，解析meta数据成功，metaUnzipPath: {}, 解析到{}个服务", progress.getProgressId(),
                    metaUnzipPath, vo.getFrameworks().stream().mapToLong(fw -> fw.getServices().size()).sum());

//            解压安装包
            log.info("【导入第三方软件源】 进度ID:{}，开始解压软件安装包", progress.getProgressId());
            File pkgFile = uploadTempFileService.getTempFile(dto.getPkgFileId());
            pkgUnzipPath = decompressPkgFile(pkgFile, vo, progress);
            log.info("【导入第三方软件源】 进度ID:{}，解压软件安装包成功, 解压路径{}", progress.getProgressId(), pkgUnzipPath);

//            保存数据
            saveFrameInfo(metaUnzipPath, vo, progress);
            log.info("【导入第三方软件源】 进度ID:{}，更新meta数据成功", progress.getProgressId());

//            移动文件
            moveFiles(metaUnzipPath, vo, pkgUnzipPath, progress);
            log.info("【导入第三方软件源】 进度ID:{}，移动安装安装文件成功", progress.getProgressId());
        } catch (Exception e) {
            error = "导入组件失败," + e.getMessage();
            log.error("import comp(meta: {}, pkg:{}) fail: ", dto.getMeteFileId(), dto.getPkgFileId(), e);
        } finally {
            if (metaUnzipPath != null) {
//                meta文件中包含了很大敏感信息，必须保证删除掉
                FileUtil.del(new File(metaUnzipPath));
            }
            if (pkgUnzipPath != null) {
                String finalPkgDir = pkgUnzipPath;
//                异步删除安装包的解压目录
                CompletableFuture.runAsync(() -> FileUtil.del(new File(finalPkgDir)));
            }
            if (StringUtils.isNoneBlank(error)) {
                progress.setState(-1);
            } else {
                progress.setState(1);
            }
            progress.setExpire(LocalDateTime.now().plusMinutes(5));
        }
    }

    private String unpackMetaFile(File metaFile, InstallComponentDTO dto, ImportCompProgressVO progress) throws IOException {
        progress.setState(2);
        progress.setTotal(10);
        String unzipDir = ZipUtils.unzipToTemp(metaFile.getAbsolutePath(), dto.getUnzipPasswd());
        progress.setStep(5);
        //      解密文件的内容
        MetaUtils.decodeMatchedFiles(unzipDir, dto.getContentDecodePasswd());
        progress.setStep(6);

        return unzipDir;
    }


    private String decompressPkgFile(File pkgFile, ExtRepoMetaFsModel vo, ImportCompProgressVO progress) throws IOException {
        progress.setState(3);
        progress.setStep(0);

        String dir = ZipUtils.unzipToTemp(pkgFile.getAbsolutePath(), null);
        progress.setStep(9);

        Set<String> packageNames = vo.getFrameworks()
                .stream()
                .flatMap(f -> f.getServices().stream())
                .map(ServiceMeta::getPackageName)
                .collect(Collectors.toSet());
        if (packageNames.isEmpty()) {
            return dir;
        }

        File pkgDir = MetaUtils.getPkgPath(dir).toFile();
        if (!pkgDir.exists() || pkgDir.isFile()) {
            throw new BusinessException("安装包目录不存在");
        }

        List<String> errors = new ArrayList<>();
        for (String pkgName : packageNames) {
            File pkg = new File(pkgDir, pkgName);
            File pkgMd5 = new File(pkgDir, MetaUtils.getMd5FileName(pkgName));
            if (!pkg.exists() || !pkgMd5.exists()) {
                errors.add(String.format("安装包%s或者其md5文件不存在", pkgName));
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
        return dir;
    }


    private void saveFrameInfo(String unzipDir, ExtRepoMetaFsModel vo, ImportCompProgressVO progress) {
        progress.setState(4);
        progress.setTotal(vo.getFrameworks().stream().mapToLong(f -> f.getServices().size()).sum());
        progress.setStep(0);

        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        vo.getFrameworks().forEach(framework -> {
            FrameInfoEntity db = frameInfoService.saveClusterFrame(framework.getFrameCode());

            framework.getServices().forEach(srv -> {
                String ddl = FileUtil.readString(Paths.get(unzipDir, srv.getDdl()).toFile(), StandardCharsets.UTF_8);
                loadServiceMeta.parseServiceDdl(db.getFrameCode(), clusters, db, srv.getName(), ddl);
                progress.setStep(progress.getStep() + 1);
            });
        });
    }


    private void moveFiles(String metaUnzipPath, ExtRepoMetaFsModel vo, String pkgPath, ImportCompProgressVO progress) {
        progress.setState(5);
        progress.setStep(0);

        Set<String> packageNames = vo.getFrameworks()
                .stream()
                .flatMap(f -> f.getServices().stream())
                .map(ServiceMeta::getPackageName)
                .collect(Collectors.toSet());
        progress.setTotal(packageNames.size() + 1);

        File metaDir = FileUtil.file(Constants.META_PATH);
        if (metaDir != null && metaDir.exists()) {
            vo.getFrameworks().stream().flatMap(fw -> fw.getServices().stream()).forEach(srv -> {
                String targetSrvDir = PathUtils.join(metaDir.toPath(), srv.getFrameCode(), srv.getName()).toString();
                FileUtil.copy(new File(metaUnzipPath, srv.getDdl()), new File(targetSrvDir, MetaUtils.SERVICE_DDL), true);
                if (StringUtils.isNotBlank(srv.getScript())) {
                    FileUtil.copy(new File(metaUnzipPath, srv.getScript()), new File(targetSrvDir), true);
                }

//                FIXME copy template dir
            });
        }
        progress.setStep(1);

        if (packageNames.isEmpty()) {
            return;
        }

        File pkgDir = MetaUtils.getPkgPath(pkgPath).toFile();
        packageNames.forEach(pkg -> {
            FileUtil.move(new File(pkgDir, pkg), new File(Constants.MASTER_MANAGE_PACKAGE_PATH, pkg), true);
            FileUtil.move(new File(pkgDir, MetaUtils.getMd5FileName(pkg)), new File(Constants.MASTER_MANAGE_PACKAGE_PATH, MetaUtils.getMd5FileName(pkg)), true);
            progress.setStep(progress.getStep() + 1);
        });
    }

    @Override
    public ImportCompProgressVO queryProgress(Integer progressId) {
        ImportCompProgressVO vo = importCmpMap.get(progressId);
        if (vo == null) {
            vo = new ImportCompProgressVO(progressId);
            vo.setState(-2);
        }
        return vo;
    }

    @Override
    public void clearProgressCache() {
        Set<Integer> keys = importCmpMap.keySet();
        for (Integer key : keys) {
            ImportCompProgressVO pg = importCmpMap.get(key);
            if (pg != null && pg.isTimeout()) {
                importCmpMap.remove(key);
            }
        }
    }

    @Override
    public DeploymentDAG buildDeploymentDAG(DeploymentDTO dto) {
        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId());
        if (deploymentFile == null) {
            throw new BusinessException("部署清单文件不存在");
        }
        String content = MetaUtils.decodeFile(deploymentFile, dto.getContentDecodePasswd());
        DeploymentModel model = MetaUtils.parseDeploymentFile(content);

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        DeploymentDAGBuildContext context = new DeploymentDAGBuildContext(clusterInfo, serviceList);


        List<String> uninstall = new ArrayList<>();
        model.getApp().forEach(app -> {
            FrameServiceEntity entity = context.getSrvEntity(app);
            if (entity == null) {
                uninstall.add(app.getName() + "(" + app.getVersion() + ")");
            }
        });
        if (!uninstall.isEmpty()) {
            throw new BusinessException(String.format("服务: %s在框架中%s不存在", StrUtil.join(";", uninstall), clusterInfo.getClusterCode()));
        }


        DeploymentDAG dag = context.buildDAG(model.getApp(), false);
        return dag;
    }

    @Override
    public List<String> deploy(DeploymentDTO dto) {
        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId());
        if (deploymentFile == null) {
            throw new BusinessException("部署清单文件不存在");
        }
        String content = MetaUtils.decodeFile(deploymentFile, dto.getContentDecodePasswd());
        DeploymentModel model = MetaUtils.parseDeploymentFile(content);
        log.info("完成解析部署文件");


        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());

        DeploymentDAGBuildContext context = new DeploymentDAGBuildContext(clusterInfo, serviceList);


//        保存serviceRole和host的映射
        List<ServiceRoleHostMapping> hostMappings = new ArrayList<>();
        model.getApp().stream()
                .flatMap(app -> app.getRoles().stream())
                .forEach(role -> {
                    ServiceRoleHostMapping hostMapping = new ServiceRoleHostMapping();
                    hostMapping.setHosts(role.getDeployHosts());
                    hostMapping.setServiceRole(role.getName());
                    hostMappings.add(hostMapping);
                });
        serviceInstallService.saveServiceRoleHostMapping(dto.getClusterId(), hostMappings);
        log.info("保存角色和host映射成功");


//        保存应用的启动配置
        model.getApp().forEach(app -> {
            List<ServiceConfig> configs = serviceInstallService.getServiceConfigOption(dto.getClusterId(), app.getName());
            Map<String, DeploySrvConfig> configMap = CollectionUtil.toMap(app.getConfig(), new HashMap<>(), DeploySrvConfig::getName);
            configs.forEach(conf -> {
                DeploySrvConfig deployConf = configMap.get(conf.getName());
                if (deployConf == null) {
                    conf.setValue(conf.getDefaultValue());
                } else {
                    conf.setValue(deployConf.getValue());
                }
            });
            serviceInstallService.saveServiceConfig(dto.getClusterId(), app.getName(), configs, null);
        });
        log.info("保存部署配置项成功");


//
        DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag = context.buildDeployDAG(model.getApp(), false);
        List<String> nodes = dag.topologicalSort();
        List<String> commandIds = new ArrayList<>(model.getApp().size());
        nodes.forEach(node -> {
            DeploymentDAG.SrvNodeVO srv = dag.getNode(node);
            commandIds.add(generateCommand(clusterInfo, context.getSrvEntity(srv.getName(), srv.getVersion())));
        });
        log.info("保存安装命令成功");

//        必须等待事务提交，否则，有概率查询不到保存的命令
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> doInstall(clusterInfo.getId(), commandIds, dag));
            }
        });
        return commandIds;
    }


    public String generateCommand(ClusterInfoEntity cluster, FrameServiceEntity frameService) {
        ClusterServiceInstanceEntity serviceInstance = clusterServiceInstanceService.getServiceInstanceByClusterIdAndServiceName(cluster.getId(), frameService.getServiceName());
        CommandType commandType = ServiceState.WAIT_INSTALL.equals(serviceInstance.getServiceState()) ? CommandType.INSTALL_SERVICE : CommandType.UPGRADE_SERVICE;
        ClusterServiceCommandEntity commandEntity = ProcessUtils.generateCommandEntity(cluster.getId(), commandType, frameService.getServiceName());
        commandEntity.setServiceInstanceId(serviceInstance.getId());
        commandService.save(commandEntity);

        Map<String, List<String>> serviceRoleHostMap = (Map<String, List<String>>) CacheUtils.get(cluster.getClusterCode() + Constants.UNDERLINE + Constants.SERVICE_ROLE_HOST_MAPPING);
        if (serviceRoleHostMap == null) {
            serviceRoleHostMap = new HashMap<>();
        }
        List<FrameServiceRoleEntity> serviceRoleList = frameServiceRoleService.getServiceRoleList(cluster.getId(), Collections.singletonList(frameService.getId()), null);

        List<ClusterServiceCommandHostEntity> commandHostList = new ArrayList<>();
        List<ClusterServiceCommandHostCommandEntity> hostCommandList = new ArrayList<>();
//        FIXME
        HashMap<String, ClusterServiceCommandHostEntity> cache = new HashMap<>();
        for (FrameServiceRoleEntity serviceRole : serviceRoleList) {
            if (serviceRoleHostMap.containsKey(serviceRole.getServiceRoleName())) {
                List<String> hosts = serviceRoleHostMap.get(serviceRole.getServiceRoleName());
                for (String hostname : hosts) {
//

                }
            }
        }
//        commandHostService.saveBatch(commandHostList);
//        hostCommandService.saveBatch(hostCommandList);

        return commandEntity.getCommandId();
    }


    private void doInstall(Integer clusterId, List<String> commandIds, DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag) {
        log.info("开始执行安装操作");
        SubmitActiveTaskNodeCommand submitActiveTaskNodeCommand = new SubmitActiveTaskNodeCommand();
        submitActiveTaskNodeCommand.setCommandType(null);
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        submitActiveTaskNodeCommand.setClusterId(clusterInfo.getId());
        submitActiveTaskNodeCommand.setClusterCode(clusterInfo.getClusterCode());

        Collection<String> beginNode = dag.getBeginNode();
        Map<String, String> readyToSubmitTaskList = new ConcurrentHashMap<>();
        for (String node : beginNode) {
            readyToSubmitTaskList.put(node, "");
        }


        ExecDAGBuilderContext context = new ExecDAGBuilderContext();


        List<ClusterServiceCommandEntity> commandList = commandService.listByIds(commandIds);
        context.setSrvCmd(commandList);


        List<ClusterServiceCommandHostCommandEntity> hostCommandList = hostCommandService.lambdaQuery()
                .in(ClusterServiceCommandHostCommandEntity::getCommandId, commandIds)
                .list();
        context.setCmdHost(hostCommandList);


        DAGGraph<String, ServiceNode, String> deployGAG = new DAGGraph<>();
        dag.getNodes().forEach((srv, info) -> {
            ServiceNode serviceNode = new ServiceNode();
            ClusterServiceCommandEntity cmd = context.getCmd(srv);
            serviceNode.setCommandId(cmd.getCommandId());

            FrameServiceEntity serviceEntity = frameService.lambdaQuery()
                    .eq(FrameServiceEntity::getFrameId, clusterId)
                    .eq(FrameServiceEntity::getServiceName, info.getName())
                    .eq(FrameServiceEntity::getServiceVersion, info.getVersion())
                    .one();

            List<ServiceRoleInfo> masterRoles = new ArrayList<>();
            List<ServiceRoleInfo> elseRoles = new ArrayList<>();

            for (ClusterServiceCommandHostCommandEntity hostCommand : context.getCmdHostList(cmd.getCommandId())) {
                FrameServiceRoleEntity frameServiceRoleEntity = frameServiceRoleService.getServiceRoleByFrameCodeAndServiceRoleName(clusterInfo.getClusterFrame(), hostCommand.getServiceRoleName());

                ServiceRoleInfo serviceRoleInfo = JSONObject.parseObject(frameServiceRoleEntity.getServiceRoleJson(), ServiceRoleInfo.class);
                serviceRoleInfo.setClusterId(clusterInfo.getId());

                serviceRoleInfo.setHostname(hostCommand.getHostname());
                serviceRoleInfo.setHostCommandId(hostCommand.getHostCommandId());

                serviceRoleInfo.setParentName(cmd.getServiceName());
                serviceRoleInfo.setCommandType(CommandType.ofCode(cmd.getCommandType()));
                serviceRoleInfo.setServiceInstanceId(cmd.getServiceInstanceId());

                serviceRoleInfo.setPackageName(serviceEntity.getPackageName());
                serviceRoleInfo.setArchInfoMap(getArchMap(serviceEntity));
                serviceRoleInfo.setDecompressPackageName(serviceEntity.getDecompressPackageName());
                serviceRoleInfo.setFrameCode(serviceEntity.getFrameCode());
                ServiceInfo serviceInfo = JSONObject.parseObject(serviceEntity.getServiceJson(), ServiceInfo.class);
                serviceRoleInfo.setCreateDecompressDir(serviceInfo.getCreateDecompressDir());

                Optional.ofNullable(ServiceRoleStrategyContext.getServiceRoleHandler(serviceRoleInfo.getName()))
                        .ifPresent(ha -> ha.handlerServiceRoleInfo(serviceRoleInfo, hostCommand.getHostname()));


                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType())) {
                    masterRoles.add(serviceRoleInfo);
                } else {
                    elseRoles.add(serviceRoleInfo);
                }
            }

            serviceNode.setMasterRoles(masterRoles);
            serviceNode.setElseRoles(elseRoles);
            deployGAG.addNode(srv, serviceNode);
        });

        dag.getEdges().forEach(edge -> {
            deployGAG.addEdge(edge.getStart(), edge.getEnd(), false);
        });

        submitActiveTaskNodeCommand.setDag(deployGAG);
        submitActiveTaskNodeCommand.setReadyToSubmitTaskList(readyToSubmitTaskList);
        log.debug("开始执行dag, submitActiveTaskNodeCommand:{}", JSONObject.toJSONString(submitActiveTaskNodeCommand));

        log.info("构建DAG完成，开始执行命令");
        ActorRef submitTaskNodeActor = ActorUtils.getLocalActor(SubmitTaskNodeActor.class, ActorUtils.getActorRefName(SubmitTaskNodeActor.class));
        submitTaskNodeActor.tell(submitActiveTaskNodeCommand, ActorRef.noSender());
    }


    private Map<String, ArchInfo> getArchMap(FrameServiceEntity srv) {
        Map<String, ArchInfo> arch;
        if (StringUtils.isNotEmpty(srv.getArch())) {
            return JSONObject.parseObject(srv.getArch(), new TypeReference<Map<String, ArchInfo>>() {
            });
        } else {
            return LoadServiceMeta.getArchInfo(srv.getPackageName(), srv.getDecompressPackageName());
        }
    }

}
