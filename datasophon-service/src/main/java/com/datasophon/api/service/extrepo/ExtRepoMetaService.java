package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;

/**
 * 软件外部源的元数据业务逻辑处理类
 * @author zhanghuangbin
 * @date 2025/11/7
 */
public interface ExtRepoMetaService {


    ImportCompProgressVO importCmp(InstallComponentDTO dto);


    ImportCompProgressVO queryProgress(Integer progressId);

    void clearProgressCache();
}
