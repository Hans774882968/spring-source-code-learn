package com.hans.aop_demo.xml;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

@SpringBootApplication
public class AOPDemoXml {
    public static void main(String[] args) {
        AbstractApplicationContext ac = new ClassPathXmlApplicationContext("aop_demo.xml");
        Student student = ac.getBean(Student.class);
        student.getName();
        student.getAge();
        ac.close();
    }
}
