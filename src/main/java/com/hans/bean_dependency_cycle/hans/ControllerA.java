package com.hans.bean_dependency_cycle.hans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerA {
    @Autowired
    private ControllerB cb;

    public ControllerB getCb() {
        return cb;
    }

    @RequestMapping("/controllerA")
    public String index() {
        return "hello controllerA!";
    }
}
