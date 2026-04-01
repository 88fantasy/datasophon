package com.datasophon.worker.test.hook;

import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.hook.nacos.InitNacosHookAction;
import com.datasophon.worker.hook.nacos.InitNacosParams;
import com.datasophon.worker.test.PropertiesPathUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
public class InitNacosTest {


    @Test
    public void testHook() {
        PropertiesPathUtils.resetPropertyFile();
        InitNacosHookAction hook = new InitNacosHookAction();

        HookContext context = new HookContext();
        context.setServiceName("PORTAL");
        context.setServiceRoleName("portal");
        context.setPackageName("portal-application-3.1.0-RELEASE");

        Map<String, Object> params = new HashMap<>();
        params.put("serverAddr", "bigdata-dev.dgmed.cn:31112");
        params.put("namespace", "vos");
        params.put("username", "nacos");
        params.put("password", "Un1c0m@gD203N@c0s");
        params.put("group", "portal");

        InitNacosParams.Resource resource = new InitNacosParams.Resource();
        resource.setPath("D:\\data\\chinaunicom\\source\\datasophon\\datasophon-api\\src\\main\\resources\\application.yml");
        params.put("resources", Arrays.asList(resource));
        context.setParams(params);
        context.setGlobalVariables(new HashMap<>());


        ExecResult result = hook.invoke(context);
        System.out.println(result.isSuccess());
    }
}
