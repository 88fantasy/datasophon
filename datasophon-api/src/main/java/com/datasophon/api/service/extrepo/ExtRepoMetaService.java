package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
import com.datasophon.api.vo.extrepo.ValidateResultVO;

/**
 * 软件外部源的元数据业务逻辑处理类
 * @author zhanghuangbin
 */
public interface ExtRepoMetaService {
    
    ValidateResultVO validMetaFile(InstallComponentDTO dto);
    
    ValidateResultVO validatePkgFile(InstallComponentDTO dto);
    
    ImportCompProgressVO importCmp(InstallComponentDTO dto);
    
    ImportCompProgressVO queryProgress(Integer progressId);
    
    void clearProgressCache();
    
}
