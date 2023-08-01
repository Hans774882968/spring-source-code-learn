package com.hans.aop_demo.anno_anno;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AOPDemoAnnoAnnoConfig {
    @Bean
    public Student getStu() {
        Student s = new Student();
        s.setAge(19);
        s.setName("AOPDemoAnnoAnnoConfig hans");
        return s;
    }
}
