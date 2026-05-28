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


package com.datasophon.api.service.impl;

import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterNodeLabelService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterNodeLabelEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.ClusterNodeLabelMapper;

import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterNodeLabelService")
@Transactional
public class ClusterNodeLabelServiceImpl extends ServiceImpl<ClusterNodeLabelMapper, ClusterNodeLabelEntity>
    implements
    ClusterNodeLabelService {

  private static final Logger logger = LoggerFactory.getLogger(ClusterNodeLabelServiceImpl.class);

  @Autowired
  private ClusterHostService hostService;

  @Autowired
  private ClusterServiceRoleInstanceMapper roleInstanceMapper;

  @Autowired
  private ClusterInfoMapper clusterInfoMapper;

  @Autowired
  private WorkerCommandClient workerCommandClient;

  @Override
  public Result saveNodeLabel(Integer clusterId, String nodeLabel) {
    if (repeatNodeLable(clusterId, nodeLabel)) {
      return Result.error(Status.REPEAT_NODE_LABEL.getMsg());
    }
    ClusterNodeLabelEntity nodeLabelEntity = new ClusterNodeLabelEntity();
    nodeLabelEntity.setClusterId(clusterId);
    nodeLabelEntity.setNodeLabel(nodeLabel);
    this.save(nodeLabelEntity);
    // refresh to yarn
    if (!refreshToYarn(clusterId, "-addToClusterNodeLabels", nodeLabel)) {
      throw new BusinessException(
          Status.ADD_YARN_NODE_LABEL_FAILED.getMsg() + ",maybe you need to enable yarn node labels");
    }
    return Result.success();
  }

  private boolean refreshToYarn(Integer clusterId, String type, String nodeLabel) {
    ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(clusterId);
    List<ClusterServiceRoleInstanceEntity> roleList =
        roleInstanceMapper.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, "ResourceManager");
    if (!roleList.isEmpty()) {
      String hostname = roleList.get(0).getHostname();
      ArrayList<String> commands = new ArrayList<>();
      commands.add(Constants.INSTALL_PATH + Constants.SLASH
          + PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "YARN") + "/bin/yarn");
      commands.add("rmadmin");
      commands.add(type);
      commands.add("\"" + nodeLabel + "\"");
      try {
        ExecResult execResult = workerCommandClient.executeCmd(hostname, commands);
        if (execResult.getExecResult()) {
          logger.info("add yarn node label success at {}", hostname);
          return true;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      logger.info("add yarn node label failed");
      return false;
    }
    return true;
  }

  @Override
  public Result deleteNodeLabel(Integer nodeLabelId) {
    ClusterNodeLabelEntity nodeLabelEntity = this.getById(nodeLabelId);

    if (nodeLabelInUse(nodeLabelEntity.getNodeLabel())) {
      return Result.error(Status.NODE_LABEL_IS_USING.getMsg());
    }
    this.removeById(nodeLabelId);
    if (!refreshToYarn(nodeLabelEntity.getClusterId(), "-removeFromClusterNodeLabels",
        nodeLabelEntity.getNodeLabel())) {
      throw new BusinessException(Status.REMOVE_YARN_NODE_LABEL_FAILED.getMsg());
    }
    return Result.success();
  }

  @Override
  public Result assignNodeLabel(Integer nodeLabelId, String hostIds) {
    ClusterNodeLabelEntity nodeLabelEntity = this.getById(nodeLabelId);
    List<String> ids = Arrays.asList(hostIds.split(","));
    hostService.updateBatchNodeLabel(ids, nodeLabelEntity.getNodeLabel());

    List<ClusterHostDO> list = hostService.getHostListByIds(ids);
    String assignNodeLabel = list.stream().map(e -> e.getHostname() + "=" + nodeLabelEntity.getNodeLabel())
        .collect(Collectors.joining(" "));
    logger.info("assign node label {}", assignNodeLabel);
    // sync to yarn
    // refresh to yarn
    if (!refreshToYarn(nodeLabelEntity.getClusterId(), "-replaceLabelsOnNode", assignNodeLabel)) {
      throw new BusinessException(Status.ASSIGN_YARN_NODE_LABEL_FAILED.getMsg());
    }
    return Result.success();
  }

  @Override
  public List<ClusterNodeLabelEntity> queryClusterNodeLabel(Integer clusterId) {
    return this.list(new QueryWrapper<ClusterNodeLabelEntity>().eq(Constants.CLUSTER_ID, clusterId));
  }

  @Override
  public void createDefaultNodeLabel(Integer clusterId) {
    ClusterNodeLabelEntity nodeLabelEntity = new ClusterNodeLabelEntity();
    nodeLabelEntity.setNodeLabel("default");
    nodeLabelEntity.setClusterId(clusterId);
    this.save(nodeLabelEntity);
  }

  private boolean nodeLabelInUse(String nodeLabel) {
    List<ClusterHostDO> list = hostService.list(new QueryWrapper<ClusterHostDO>()
        .eq(Constants.NODE_LABEL, nodeLabel));
    if (list.size() > 0) {
      return true;
    }
    return false;
  }

  private boolean repeatNodeLable(Integer clusterId, String nodeLabel) {
    List<ClusterNodeLabelEntity> list = this.list(new QueryWrapper<ClusterNodeLabelEntity>()
        .eq(Constants.CLUSTER_ID, clusterId)
        .eq(Constants.NODE_LABEL, nodeLabel));
    if (list.size() > 0) {
      return true;
    }
    return false;
  }
}
