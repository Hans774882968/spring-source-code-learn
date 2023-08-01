package com.hans.cglib_demo;

import org.springframework.cglib.proxy.Enhancer;

public class CglibDemo {
    public static void main(String[] args) {
        // 创建 Enhancer 对象，类似于 JDK 动态代理的 Proxy 类
        Enhancer enhancer = new Enhancer();
        // 设置目标类的字节码文件
        enhancer.setSuperclass(Student.class);
        // 设置回调函数
        enhancer.setCallback(new LogInterceptor());
        // create 方法正式创建代理类
        Student student = (Student) enhancer.create();
        student.setAge(13);
        student.setName("hans");
        for (int i = 0; i < 4; i++)
            student.setAge(student.getAge());
        System.out.println(student.getAge());
        System.out.println(student.getName());
    }
}
