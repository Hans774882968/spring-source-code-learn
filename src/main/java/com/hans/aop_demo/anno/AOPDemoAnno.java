package com.hans.aop_demo.anno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class AOPDemoAnno {
    public static void main(String[] args) {
        ConfigurableApplicationContext cac = SpringApplication.run(AOPDemoAnno.class, args);
        Student student = cac.getBean(Student.class);
        System.out.printf("Name = %s, Age = %d\n", student.getName(), student.getAge());
    }
}
