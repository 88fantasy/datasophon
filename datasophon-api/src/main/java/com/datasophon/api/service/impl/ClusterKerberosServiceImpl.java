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

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.service.ClusterKerberosService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cn.hutool.core.io.FileUtil;

@Service("clusterKerberosService")
@Transactional
public class ClusterKerberosServiceImpl implements ClusterKerberosService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterKerberosServiceImpl.class);
    
    private static final String SSHUSER = "SSHUSER";
    
    private static final String KEYTAB_PATH = "/etc/security/keytab";
    
    @Autowired
    private ClusterServiceRoleInstanceService roleInstanceService;
    
    @Override
    public void downloadKeytab(
                               Integer clusterId,
                               String principal,
                               String keytabName,
                               String hostname,
                               HttpServletResponse response) throws IOException {
        String keytabFilePath =
                KEYTAB_PATH + Constants.SLASH + hostname + Constants.SLASH + keytabName;
        File file = new File(keytabFilePath);
        if (!file.exists()) {
            generateKeytabFile(clusterId, keytabFilePath, principal);
        }
        FileInputStream inputStream = new FileInputStream(file);
        response.reset();
        response.setContentType("application/octet-stream");
        response.addHeader("Content-Length", "" + file.length());
        response.setHeader("Content-Disposition", "attachment;filename=" + keytabName);
        OutputStream out = response.getOutputStream();
        try {
            int length = 0;
            byte[] buffer = new byte[1024];
            while ((length = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
        } finally {
            inputStream.close();
            out.flush();
            out.close();
        }
    }
    
    @Override
    public void uploadKeytab(MultipartFile file, String hostname, String keytabFileName) throws IOException {
        String keytabFilePath =
                KEYTAB_PATH + Constants.SLASH + hostname + Constants.SLASH + keytabFileName;
        file.transferTo(new File(keytabFilePath));
    }
    
    private void generateKeytabFile(
                                    Integer clusterId,
                                    String keytabFilePath,
                                    String principal) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        String kadminPrincipal = globalVariables.get("${kadminPrincipal}");
        String kadminPassword = globalVariables.get("${kadminPassword}");
        String listPrinc = "kadmin -p" + kadminPrincipal + " -w" + kadminPassword + " -q \"listprincs\"";
        ExecResult execResult = ShellUtils.execShell(listPrinc);
        String execOut = execResult.getExecOut();
        if (!execOut.contains(principal)) {
            String addprinc = "kadmin -p" + kadminPrincipal + " -w" + kadminPassword + " -q \"addprinc -randkey "
                    + principal + "\"";
            logger.info("add principal cmd is : {}", addprinc);
            ShellUtils.execShell(addprinc);
        }
        if (!FileUtil.exist(keytabFilePath)) {
            FileUtil.mkParentDirs(keytabFilePath);
        }
        String keytabCmd =
                "kadmin -p" + kadminPrincipal + " -w" + kadminPassword + " -q \"xst -k " + keytabFilePath + " "
                        + principal + "\"";
        logger.info("generate keytab file cmd is : {}", keytabCmd);
        ShellUtils.execShell(keytabCmd);
        
    }
}
