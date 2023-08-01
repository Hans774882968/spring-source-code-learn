package com.hans.aop_demo.anno;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AOPDemoConfig {
    @Bean
    public Student getStu() {
        Student s = new Student();
        s.setAge(18);
        s.setName("AOPDemoConfig hans");
        return s;
    }
}
