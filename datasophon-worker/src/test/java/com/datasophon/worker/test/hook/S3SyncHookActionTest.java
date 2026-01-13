package com.datasophon.worker.test.hook;

import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.hook.s3.S3SyncHookAction;
import com.datasophon.worker.test.PropertiesPathUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
public class S3SyncHookActionTest {


    @Test
    public void test1() throws Exception {
        PropertiesPathUtils.resetPropertyFile();
        S3SyncHookAction hook = new S3SyncHookAction();

        HookContext context = new HookContext();
        context.setServiceName("PORTAL");
        context.setServiceRoleName("portal");
        context.setPackageName("portal-application-3.1.0-RELEASE");

        Map<String, Object> params = new HashMap<>();
        params.put("endpoint", "http://192.168.2.230:9000");
        params.put("accessKey", "cuminio");
        params.put("secretKey", "u4Gkp19TRcKKlTCLNA1pyA==");
        params.put("bucket", "test");
        params.put("resourcePath", "D:\\data\\联通工作\\软件发布包\\门户发包\\V3.1.0-2025-7-25(稳定版)");
        context.setParams(params);
        context.setGlobalVariables(new HashMap<>());


        ExecResult result = hook.invoke(context);
        System.out.println(result.isSuccess());
    }
}
