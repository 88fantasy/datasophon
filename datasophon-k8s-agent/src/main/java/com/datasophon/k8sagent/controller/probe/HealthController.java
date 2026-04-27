package com.datasophon.k8sagent.controller.probe;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class HealthController {

    /**
     * 健康检查端点
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("component", "datasophon-k8s-agent");
        log.debug("Health check requested");
        return status;
    }

    /**
     * 就绪检查端点
     *
     * @return 就绪状态
     */
    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> status = new HashMap<>();
        status.put("ready", true);
        status.put("component", "datasophon-k8s-agent");
        log.debug("Ready check requested");
        return status;
    }
}
