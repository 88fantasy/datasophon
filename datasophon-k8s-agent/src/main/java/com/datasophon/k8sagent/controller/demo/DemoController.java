package com.datasophon.k8sagent.controller.demo;

import com.datasophon.k8sagent.dto.EchoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanghuangbin
 */
@Slf4j
@RestController
@RequestMapping("/demo")
public class DemoController {


    @PostMapping("/echo")
    public String echo(@RequestBody EchoDTO echo) {
        return "reply: " + echo.getEcho();
    }
}
