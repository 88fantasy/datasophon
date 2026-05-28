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


package com.datasophon.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.datasophon.dao.entity.FrameServiceEntity;
import org.apache.hadoop.util.VersionUtil;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 集群框架版本服务表
 * 
 * @author gaodayu
 * @email gaodayu2022@163.com
 * @date 2022-03-15 17:36:08
 */
@Mapper
public interface FrameServiceMapper extends BaseMapper<FrameServiceEntity> {

  default FrameServiceEntity getServiceByFrameCodeAndServiceName(String clusterFrame, String serviceName) {
//        changlog: 旧版本没有版本之分，新需求有了版本，为了兼容，约定使用最新版本
    List<FrameServiceEntity> list = selectList(Wrappers.<FrameServiceEntity>lambdaQuery()
        .eq(FrameServiceEntity::getFrameCode, clusterFrame)
        .eq(FrameServiceEntity::getServiceName, serviceName));
    list.sort((s1, s2) -> VersionUtil.compareVersions(s1.getServiceVersion(), s2.getServiceVersion()));
//        返回版本最新的定义
    return list.get(list.size() - 1);
  }
}
