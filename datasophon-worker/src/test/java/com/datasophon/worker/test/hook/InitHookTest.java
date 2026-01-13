package com.datasophon.worker.test.hook;

import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.hook.db.InitDbHook;
import com.datasophon.worker.test.PropertiesPathUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
public class InitHookTest {


    @Test
    public void testHook() {
        PropertiesPathUtils.resetPropertyFile();
        InitDbHook hook = new InitDbHook();

        HookContext context = new HookContext();
        context.setServiceName("PORTAL");
        context.setServiceRoleName("portal");
        context.setPackageName("portal-application-3.1.0-RELEASE");

        Map<String, Object> params = new HashMap<>();
        params.put("url", "jdbc:mysql://192.168.2.202:3306/datasophonmigratetest?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2b8&autoReconnect=true&failOverReadOnly=false&allowMultiQueries=true&rewriteBatchedStatements=true");
        params.put("driver", "com.mysql.cj.jdbc.Driver");
        params.put("username", "datasophon");
        params.put("password", "fI5sQ4yQ4fP5");
        params.put("resourceKey", "datasophonmigratetest");
        params.put("scriptPath", "D:\\Desktop\\VOS集成测试\\全量测试\\packages_20260108151129\\packages\\raw\\portal-application-3.1.0-RELEASE\\dbtest");
        params.put("metaStorage", "datasophon");
        context.setParams(params);
        context.setGlobalVariables(new HashMap<>());


        ExecResult result = hook.invoke(context);
        System.out.println(result.isSuccess());
    }
}
