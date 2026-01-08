package com.datasophon.api.service.extrepo.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.LoadServiceMeta;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.extrepo.ExtRepoMetaService;
import com.datasophon.api.service.extrepo.ctx.DeploymentDAGBuildContext;
import com.datasophon.api.service.extrepo.ctx.MetaParseOption;
import com.datasophon.api.service.extrepo.ctx.SrvDependenciesContext;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.vo.extrepo.DeploymentDAG;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.Constants;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.PackageStorageUtils;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.TarUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            unzipDir = TarUtils.decompressToTemp(metaFile.getAbsolutePath());
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
            List<String> fileNames = TarUtils.getEntry(pkgFile.getAbsolutePath());
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
        if (dto.getPkgFileId() != null) {
            File pkg = uploadTempFileService.getTempFile(dto.getPkgFileId());
            if (pkg == null) {
                throw new BusinessException("安装包文件不存在");
            }
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
            if (dto.getPkgFileId() != null) {
                log.info("【导入第三方软件源】 进度ID:{}，开始解压软件安装包", progress.getProgressId());
                File pkgFile = uploadTempFileService.getTempFile(dto.getPkgFileId());
                pkgUnzipPath = decompressPkgFile(pkgFile, vo, progress);
                log.info("【导入第三方软件源】 进度ID:{}，解压软件安装包成功, 解压路径{}", progress.getProgressId(), pkgUnzipPath);
            }


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
                progress.setError(error);
            } else {
                progress.setState(1);
            }
            progress.setExpire(LocalDateTime.now().plusMinutes(5));
        }
    }

    private String unpackMetaFile(File metaFile, InstallComponentDTO dto, ImportCompProgressVO progress) throws IOException {
        progress.setState(2);
        progress.setTotal(10);
        String unzipDir = TarUtils.decompressToTemp(metaFile.getAbsolutePath());
        progress.setStep(5);
        //      解密文件的内容
        MetaUtils.decodeMatchedFiles(unzipDir, dto.getContentDecodePasswd());
        progress.setStep(6);

        return unzipDir;
    }


    private String decompressPkgFile(File pkgFile, ExtRepoMetaFsModel vo, ImportCompProgressVO progress) throws IOException {
        progress.setState(3);
        progress.setStep(0);

        String dir = TarUtils.decompressToTemp(pkgFile.getAbsolutePath());
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
            boolean exists = frameInfoService.exists(framework.getFrameCode());
            FrameInfoEntity db = frameInfoService.saveFrameIfAbsent(framework.getFrameCode());
            if (!exists) {
                loadServiceMeta.initFramework(db);
            }

            framework.getServices().forEach(srv -> {
                String ddl = FileUtil.readString(Paths.get(unzipDir, srv.getDdl()).toFile(), StandardCharsets.UTF_8);
                loadServiceMeta.parseServiceDdl(db.getFrameCode(), clusters, db, srv.getName(), ddl);
                progress.setStep(progress.getStep() + 1);
            });
        });
    }


    private void moveFiles(String metaUnzipPath, ExtRepoMetaFsModel vo, String pkgPath, ImportCompProgressVO progress) throws IOException {
        progress.setState(5);
        progress.setStep(0);

        Set<String> packageNames = vo.getFrameworks()
                .stream()
                .flatMap(f -> f.getServices().stream())
                .map(ServiceMeta::getPackageName)
                .collect(Collectors.toSet());
        progress.setTotal(pkgPath == null ? 1 : packageNames.size() + 1);

        File metaDir = FileUtil.file(Constants.META_PATH);
        if (metaDir != null && metaDir.exists()) {
            if (StrUtil.isNotBlank(vo.getMeta())) {
                FileUtil.copy(PathUtils.join(metaUnzipPath, vo.getMeta()).toFile(), metaDir.getParentFile(), true);
            }
        }

        PackageStorage packageStorage = PackageStorageUtils.getStorage();
        if (StrUtil.isNotBlank(vo.getTemplate())) {
            File dir = PathUtils.join(metaUnzipPath, vo.getTemplate()).toFile();
            log.info("开始上传模板：{}", dir.getAbsolutePath());
            packageStorage.moveToStorage(dir,true);
        }
        progress.setStep(1);

        if (pkgPath != null) {
            File pkgDir = MetaUtils.getPkgPath(pkgPath).toFile();
            for (String pkg : packageNames) {
                log.info("上传安装软件：{}/{}", pkgDir, pkg);
                packageStorage.moveToStorage(new File(pkgDir, pkg), file-> "packages");
                packageStorage.moveToStorage(new File(pkgDir, MetaUtils.getMd5FileName(pkg)), file-> "packages");
                progress.setStep(progress.getStep() + 1);
            }
        }

        progress.setStep(progress.getTotal());
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
    public DeploymentDAG  buildDeploymentDAG(DeploymentDTO dto) {
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



}
