/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.security;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ManageMode;

import org.springframework.stereotype.Component;

/** 接管集群写操作的 Service 层门禁，不依赖 Controller 路径形态。 */
@Component
public class ImportedClusterWriteGuard {

    private final ClusterInfoService clusterInfoService;

    public ImportedClusterWriteGuard(ClusterInfoService clusterInfoService) {
        this.clusterInfoService = clusterInfoService;
    }

    /**
     * 确认目标集群允许写入。
     *
     * @param clusterId 集群 ID
     * @param action    操作说明
     */
    public void requireWritable(Integer clusterId, String action) {
        if (clusterId == null) {
            return;
        }
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        if (cluster == null || !ManageMode.IMPORTED.equals(cluster.getManageMode())) {
            return;
        }
        throw new BusinessHintException(
                String.format("集群「%s」是接管模式，只提供只读监控，不能%s。"
                        + "如需变更请在目标集群自行操作。",
                        cluster.getClusterName(), action));
    }
}
