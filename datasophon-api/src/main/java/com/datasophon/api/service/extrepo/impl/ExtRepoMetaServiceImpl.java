package com.datasophon.api.service.extrepo.impl;

import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.api.service.extrepo.ExtRepoMetaService;
import com.datasophon.api.service.extrepo.ctx.MetaParseOption;
import com.datasophon.api.service.extrepo.ctx.SrvDependenciesContext;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.utils.TransactionalUtils;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.k8s.vo.docker.LoadImageResult;
import com.datasophon.common.storage.HelmStorage;
import com.datasophon.common.storage.ImageStorage;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.TarUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.model.extrepo.ExtRepoMetaFsModel;
import com.datasophon.dao.model.extrepo.FrameworkMeta;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.CryptoException;

/**
 * @author zhanghuangbin
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
    private DdlMetaService ddlMetaService;
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private FrameServiceService frameServiceService;
    
    @Autowired
    private TransactionalUtils transactionalUtils;
    
    @Autowired
    private FrameK8sServiceService frameK8sServiceService;
    
    @Override
    public ValidateResultVO validMetaFile(InstallComponentDTO dto) {
        return unzipMetaFile(dto, unzipDir -> {
            MetaParseOption option = new MetaParseOption();
            option.setRoot(unzipDir);
            ExtRepoMetaFsModel model = MetaUtils.parseRepoMeta(option);
            
            SrvDependenciesContext ctx = new SrvDependenciesContext();
            List<String> frames = model.getFrameworks().stream().map(FrameworkMeta::getFrameCode).collect(Collectors.toList());
            List<FrameInfoEntity> frameDbs = frameInfoService.lambdaQuery().in(FrameInfoEntity::getFrameCode, frames).list();
            
            List<FrameServiceEntity> installedSrv = frameServiceService.listSimpleService(frames);
            ctx.addService(installedSrv);
            model.getFrameworks().stream()
                    .flatMap(f -> f.getVosDdlServices().stream())
                    .forEach(srv -> ctx.addVosService(srv.getFrameCode(), srv.getName()));
            
            List<String> errors = new ArrayList<>();
            model.getFrameworks().stream().flatMap(f -> f.getVosDdlServices().stream()).forEach(srv -> {
                errors.addAll(ctx.validVosDdlDependency(srv));
            });
            
            Map<Integer, FrameInfoEntity> map = CollectionUtil.toMap(frameDbs, new HashMap<>(), FrameInfoEntity::getId);
            List<FrameK8sServiceEntity> installedK8sSrv = frameK8sServiceService.listSimpleService(frameDbs.stream().map(FrameInfoEntity::getId).collect(Collectors.toList()));
            installedK8sSrv.forEach(srv -> ctx.addK8sService(map.get(srv.getFrameId()).getFrameCode(), srv.getServiceName()));
            
            model.getFrameworks().stream().flatMap(f -> f.getK8sDdLServices().stream()).forEach(srv -> ctx.addK8sService(srv.getFrameCode(), srv.getName()));
            model.getFrameworks().stream().flatMap(f -> f.getK8sDdLServices().stream()).forEach(srv -> {
                errors.addAll(ctx.validK8sDependency(srv));
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
        } catch (CryptoException e) {
            if (e.getCause() instanceof BadPaddingException) {
                throw new BusinessException("密码错误");
            }
            throw e;
        } catch (IOException e) {
            throw new BusinessException("IO异常" + e.getMessage(), e);
        } finally {
            if (unzipDir != null) {
                // meta文件中包含了很大敏感信息，必须保证删除掉
                FileUtil.del(new File(unzipDir));
            }
        }
    }
    
    @Override
    public ValidateResultVO validatePkgFile(InstallComponentDTO dto) {
        // ExtRepoMetaFsModel model = unzipMetaFile(dto, unzipDir -> {
        // MetaParseOption option = new MetaParseOption();
        // option.setRoot(unzipDir);
        // return MetaUtils.parseRepoMeta(option);
        // });
        //
        // File pkgFile = uploadTempFileService.getTempFile(dto.getPkgFileId());
        // try {
        // List<String> fileNames = TarUtils.getEntry(pkgFile.getAbsolutePath());
        // List<String> errors = new ArrayList<>();
        // model.getFrameworks().stream().flatMap(f -> f.getServices().stream()).forEach(srv -> {
        // srv.getPackageNames().forEach(pkgName -> {
        // String filePath = PathUtils.unixStyle(MetaUtils.getFileRelativePath(pkgName));
        // if (!fileNames.contains(filePath)) {
        // errors.add(filePath);
        // }
        // String md5Path = PathUtils.unixStyle(MetaUtils.getMd5FileRelativePath(pkgName));
        // if (!fileNames.contains(md5Path)) {
        // errors.add(md5Path);
        // }
        // });
        // });
        //
        // return new ValidateResultVO(errors.stream().map(e -> String.format("压缩包中缺少%s文件", e)).collect(Collectors.toList()));
        // } catch (IOException e) {
        // log.info("解压文件{}失败, {}", pkgFile.getAbsolutePath(), e.getMessage(), e);
        // throw new BusinessException("IO异常" + e.getMessage(), e);
        // }
        // pms并不会将全部文件导出，不检查文件是否存在
        return new ValidateResultVO(new ArrayList<>(0));
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
        
        PathPair pathPair = new PathPair();
        try {
            transactionalUtils.doInNewTx(() -> {
                log.info("【导入第三方软件源】 进度ID:{}，开始解析meta数据", progress.getProgressId());
                // 解析元数据
                File metaFile = uploadTempFileService.getTempFile(dto.getMeteFileId());
                String metaUnzipPath = unpackMetaFile(metaFile, dto, progress);
                pathPair.setMetaUnzipPath(metaUnzipPath);
                
                MetaParseOption option = new MetaParseOption();
                option.setRoot(metaUnzipPath);
                ExtRepoMetaFsModel vo = MetaUtils.parseRepoMeta(option);
                progress.setStep(9);
                log.info("【导入第三方软件源】 进度ID:{}，解析meta数据成功，metaUnzipPath: {}, 解析到{}个服务", progress.getProgressId(),
                        metaUnzipPath, vo.getFrameworks().stream().mapToLong(fw -> fw.getVosDdlServices().size()).sum());
                
                // 解压安装包
                String pkgUnzipPath = null;
                if (dto.getPkgFileId() != null) {
                    log.info("【导入第三方软件源】 进度ID:{}，开始解压软件安装包", progress.getProgressId());
                    File pkgFile = uploadTempFileService.getTempFile(dto.getPkgFileId());
                    pkgUnzipPath = decompressPkgFile(pkgFile, vo, progress);
                    pathPair.setPkgUnzipPath(pkgUnzipPath);
                    log.info("【导入第三方软件源】 进度ID:{}，解压软件安装包成功, 解压路径{}", progress.getProgressId(), pkgUnzipPath);
                }
                
                // 保存数据
                saveFrameInfo(metaUnzipPath, vo, progress);
                log.info("【导入第三方软件源】 进度ID:{}，更新meta数据成功", progress.getProgressId());
                
                // 移动文件
                moveFiles(metaUnzipPath, vo, pkgUnzipPath, progress);
                
                log.info("【导入第三方软件源】 进度ID:{}，移动安装安装文件成功", progress.getProgressId());
            });
        } catch (Exception e) {
            error = "导入组件失败," + e.getMessage();
            log.error("import comp(meta: {}, pkg:{}) fail: ", dto.getMeteFileId(), dto.getPkgFileId(), e);
        } finally {
            if (pathPair.getMetaUnzipPath() != null) {
                // meta文件中包含了很大敏感信息，必须保证删除掉
                FileUtil.del(new File(pathPair.getMetaUnzipPath()));
            }
            if (pathPair.getPkgUnzipPath() != null) {
                // 异步删除安装包的解压目录
                CompletableFuture.runAsync(() -> FileUtil.del(new File(pathPair.getPkgUnzipPath())));
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
    
    @Data
    private static class PathPair {
        private String metaUnzipPath;
        private String pkgUnzipPath;
    }
    
    private String unpackMetaFile(File metaFile, InstallComponentDTO dto, ImportCompProgressVO progress) throws IOException {
        progress.setState(2);
        progress.setTotal(10);
        String unzipDir = TarUtils.decompressToTemp(metaFile.getAbsolutePath());
        progress.setStep(5);
        // 解密文件的内容
        MetaUtils.decodeMatchedFiles(unzipDir, dto.getContentDecodePasswd());
        progress.setStep(6);
        
        return unzipDir;
    }
    
    private String decompressPkgFile(File pkgFile, ExtRepoMetaFsModel vo, ImportCompProgressVO progress) throws IOException {
        progress.setState(3);
        progress.setStep(0);
        progress.setTotal(10);
        
        String dir = TarUtils.decompressToTemp(pkgFile.getAbsolutePath());
        progress.setStep(10);
        
        // 不在检查文件是否存在
        // Set<String> packageNames = vo.getFrameworks()
        // .stream()
        // .flatMap(f -> f.getServices().stream())
        // .flatMap(s -> s.getPackageNames().stream())
        // .collect(Collectors.toSet());
        // if (packageNames.isEmpty()) {
        // return dir;
        // }
        //
        // File pkgDir = MetaUtils.getPkgPath(dir).toFile();
        // if (!pkgDir.exists() || pkgDir.isFile()) {
        // throw new BusinessException("安装包目录不存在");
        // }
        //
        // List<String> errors = new ArrayList<>();
        // for (String pkgName : packageNames) {
        // File pkg = new File(pkgDir, pkgName);
        // File pkgMd5 = new File(pkgDir, MetaUtils.getMd5FileName(pkgName));
        // if (!pkg.exists() || !pkgMd5.exists()) {
        // errors.add(String.format("安装包%s或者其md5文件不存在", pkgName));
        // }
        // }
        //
        // if (!errors.isEmpty()) {
        // throw new BusinessException(errors);
        // }
        return dir;
    }
    
    private void saveFrameInfo(String unzipDir, ExtRepoMetaFsModel vo, ImportCompProgressVO progress) {
        progress.setState(4);
        
        long total = vo.getFrameworks().stream().mapToLong(f -> f.getVosDdlServices().size()).sum()
                + vo.getFrameworks().stream().mapToLong(f -> f.getK8sDdLServices().size()).sum();
        progress.setTotal(total);
        progress.setStep(0);
        
        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        vo.getFrameworks().forEach(framework -> {
            FrameInfoEntity db = ddlMetaService.initFramework(framework.getFrameCode());
            framework.getVosDdlServices().forEach(srv -> {
                String ddl = FileUtil.readString(Paths.get(unzipDir, srv.getDdl()).toFile(), StandardCharsets.UTF_8);
                try {
                    ddlMetaService.loadServicePhysicalDdl(clusters, db, srv.getName(), ddl);
                } catch (Exception e) {
                    throw new BusinessException(String.format("解析服务%s的定义失败，%s。请检测service_ddl.json是否符合定义", srv.getName(), e.getMessage()), e);
                }
                
                progress.setStep(progress.getStep() + 1);
            });
            
            framework.getK8sDdLServices().forEach(srv -> {
                String ddl = FileUtil.readString(Paths.get(unzipDir, srv.getManifest()).toFile(), StandardCharsets.UTF_8);
                try {
                    ddlMetaService.loadServiceK8sDdl(db, srv.getName(), ddl);
                } catch (Exception e) {
                    throw new BusinessException(String.format("解析服务%s的定义失败，%s。请检测manifest.yaml是否符合定义", srv.getName(), e.getMessage()), e);
                }
                
                progress.setStep(progress.getStep() + 1);
            });
        });
    }
    
    private void moveFiles(String metaUnzipPath, ExtRepoMetaFsModel vo, String pkgPath, ImportCompProgressVO progress) throws IOException {
        progress.setState(5);
        progress.setStep(0);
        
        if (pkgPath == null) {
            progress.setTotal(1);
        } else {
            File pkgDir = MetaUtils.getPkgPath(pkgPath).toFile();
            File[] files = pkgDir.listFiles();
            progress.setTotal(1 + (files == null ? 0 : files.length));
        }
        
        MetaStorage metaStorage = StorageUtils.getMetaStorage();
        log.info("开始上传meta信息....");
        metaStorage.moveToStorage(PathUtils.join(metaUnzipPath, vo.getMeta()).toFile(), true);
        
        PackageStorage packageStorage = StorageUtils.getPackageStorage();
        if (StrUtil.isNotBlank(vo.getTemplate())) {
            File dir = PathUtils.join(metaUnzipPath, vo.getTemplate()).toFile();
            log.info("开始上传模板：{}", dir.getAbsolutePath());
            packageStorage.moveToStorage(dir, true);
        }
        progress.setStep(1);
        
        if (pkgPath == null) {
            return;
        }
        
        File pkgDir = MetaUtils.getPkgPath(pkgPath).toFile();
        File[] rawPkgFiles = pkgDir.listFiles();
        if (rawPkgFiles != null) {
            log.info("开始上传软件安装包(非k8s版)...");
            for (File file : rawPkgFiles) {
                log.info("上传文件{}到nexus", file.getAbsolutePath());
                packageStorage.moveToStorage(file, f -> {
                    String relativePath = PathUtils.relative(file.getParent(), pkgDir.getAbsolutePath());
                    return "packages/" + PathUtils.unixStyle(relativePath);
                });
                progress.setStep(progress.getStep() + 1);
            }
        }
        progress.setStep(progress.getTotal());
        log.info("【导入第三方软件源】 进度ID:{}，上传安装包到nexus成功", progress.getProgressId());
        
        File imageDir = MetaUtils.getImagePath(pkgPath).toFile();
        List<File> imageFiles = FileUtil.loopFiles(imageDir);
        if (imageFiles.isEmpty()) {
            return;
        }
        progress.setState(6);
        progress.setStep(0);
        progress.setTotal(100);
        ImageStorage imageStorage = StorageUtils.getImageStorage();
        log.info("开始上传镜像文件...");
        imageStorage.pushImages(imageDir, new ImageStorage.PushCallback() {
            
            @Override
            public void onEntryLoad(File file, double delta) {
                log.info("【导入第三方软件源】进度 ID:{}，加载镜像文件：{}, 任务进度：{}%",
                        progress.getProgressId(), file.getName(), (int) (delta * 100));
                long step = (long) (delta * 0.3 * progress.getTotal());
                progress.setStep((progress.getStep() + step));
            }
            
            @Override
            public void onEntryPush(LoadImageResult image, double delta) {
                log.info("【导入第三方软件源】进度 ID:{}，推送镜像：{}, 任务进度：{}%",
                        progress.getProgressId(), image.getNewQualifierImage(), (int) (delta * 100));
                long step = (long) (delta * 0.4 * progress.getTotal());
                progress.setStep((progress.getStep() + step));
            }
            
            @Override
            public void onManifest(String imageId, double delta) {
                log.info("【导入第三方软件源】进度 ID:{}，上传镜像 manifest: {}, 任务进度：{}%",
                        progress.getProgressId(), imageId, (int) (delta * 100));
                long step = (long) (delta * 0.3 * progress.getTotal());
                progress.setStep((progress.getStep() + step));
            }
        });
        log.info("【导入第三方软件源】进度 ID:{}，完成所有镜像推送操作", progress.getProgressId());
        progress.setStep(progress.getTotal());
        log.info("【导入第三方软件源】 进度ID:{}，上传镜像包成功", progress.getProgressId());
        
        long total = vo.getFrameworks().stream()
                .flatMap(f -> f.getK8sDdLServices().stream())
                .mapToLong(s -> s.getCharts().size())
                .sum();
        if (total > 0) {
            progress.setState(7);
            progress.setTotal(total);
            progress.setStep(0);
            
            HelmStorage helmStorage = StorageUtils.getHelmStorage();
            
            vo.getFrameworks().forEach(frame -> {
                frame.getK8sDdLServices().forEach(srv -> {
                    srv.getCharts().forEach(chart -> {
                        File file = Paths.get(metaUnzipPath).resolve(srv.getManifest()).getParent().resolve(chart).toFile();
                        if (file.exists()) {
                            log.info("上传文件{}到nexus的helm仓库", file.getAbsolutePath());
                            try {
                                helmStorage.pushHelm(file);
                            } catch (IOException e) {
                                throw new IllegalStateException(String.format("上传helm包%s到helm仓库失败, %s", chart, e.getMessage()), e);
                            }
                        }
                        progress.setStep(progress.getStep() + 1);
                    });
                });
            });
            progress.setStep(progress.getTotal());
            log.info("【导入第三方软件源】 进度ID:{}，上传helm包成功", progress.getProgressId());
        }
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
    
}
