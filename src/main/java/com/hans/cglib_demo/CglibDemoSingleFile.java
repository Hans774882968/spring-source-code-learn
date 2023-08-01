package com.hans.cglib_demo;

import java.lang.reflect.Method;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
class Programmer {
    private String name;
    private Integer age;
}

public class CglibDemoSingleFile {
    public static void main(String[] args) {
        // 创建 Enhancer 对象，类似于 JDK 动态代理的 Proxy 类
        Enhancer enhancer = new Enhancer();
        // 设置目标类的字节码文件
        enhancer.setSuperclass(Programmer.class);
        // 设置回调函数
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy)
                    throws Throwable {
                Long startTime = System.currentTimeMillis();
                // 注意这里是调用 invokeSuper 而不是 invoke ，否则死循环
                // methodProxy.invokeSuper 执行的是原始类的方法，method.invoke 执行的是子类的方法
                if (method.getName().equals("setAge")) {
                    if (args.length == 1 && args[0] instanceof Integer) {
                        int originalAge = (Integer) (args[0]);
                        args[0] = Integer.valueOf(originalAge + 1);
                    }
                }
                Object result = methodProxy.invokeSuper(obj, args);
                Long endTime = System.currentTimeMillis();
                System.out.printf("%s:: Execution time: %d ms\n", method.getName(), endTime - startTime);
                return result;
            }
        });
        // create 方法正式创建代理类
        Programmer programmer = (Programmer) enhancer.create(
                new Class[] { String.class, Integer.class },
                new Object[] { "hans", 15 });
        for (int i = 0; i < 3; i++)
            programmer.setAge(programmer.getAge());
        System.out.println(programmer.getAge());
        System.out.println(programmer.getName());
    }

}
