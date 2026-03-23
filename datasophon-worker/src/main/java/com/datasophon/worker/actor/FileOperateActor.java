/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.worker.actor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import com.datasophon.common.command.FileOperateCommand;
import com.datasophon.common.utils.ExecResult;

import java.io.File;
import java.nio.charset.Charset;
import java.util.TreeSet;

public class FileOperateActor extends HookTypedActor<FileOperateCommand> {

    @Override
    protected void doOnReceive(FileOperateCommand fileOperateCommand) throws Throwable {
        ExecResult execResult = new ExecResult();
        TreeSet<String> lines = fileOperateCommand.getLines();
        if (CollectionUtil.isNotEmpty(lines)) {
            File file = FileUtil.writeLines(lines, fileOperateCommand.getPath(), Charset.defaultCharset());
            if (file.exists()) {
                execResult.setExecResult(true);
            }
        } else {
            FileUtil.writeUtf8String(fileOperateCommand.getContent(), fileOperateCommand.getPath());
        }
        getSender().tell(execResult, getSelf());
    }
}
