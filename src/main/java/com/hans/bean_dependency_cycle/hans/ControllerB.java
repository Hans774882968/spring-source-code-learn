package com.hans.bean_dependency_cycle.hans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerB {
    @Autowired
    private ControllerA ca;

    public ControllerA getCa() {
        return ca;
    }

    @RequestMapping("/controllerB")
    public String index() {
        return "hello controllerB!";
    }
}
