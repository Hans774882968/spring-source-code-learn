package com.hans.aop_demo.anno;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aopDemo/anno")
public class NewController {
    @RequestMapping("")
    public String index() throws InterruptedException {
        Thread.sleep(10);
        return "aop demo";
    }
}
