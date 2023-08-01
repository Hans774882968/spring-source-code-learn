package com.hans.bean_dependency_cycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class AnotherEntry {
    public static void main(String[] args) {
        ConfigurableApplicationContext cac = SpringApplication.run(AnotherEntry.class, args);
        ControllerA beanA = cac.getBean(ControllerA.class);
        System.out.println(beanA);
        System.out.println(beanA.getCb());
        ControllerB beanB = cac.getBean(ControllerB.class);
        System.out.println(beanB);
        System.out.println(beanB.getCa());
    }
}
