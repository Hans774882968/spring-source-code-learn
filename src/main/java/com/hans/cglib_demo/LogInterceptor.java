package com.hans.cglib_demo;

import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

public class LogInterceptor implements MethodInterceptor {
    /**
     *
     * @param obj
     *            表示要进行增强的对象
     * @param method
     *            表示拦截的方法
     * @param args
     *            数组表示参数列表，基本数据类型需要传入其包装类型，如int-->Integer、long-->Long、double-->Double
     * @param methodProxy
     *            表示对方法的代理，invokeSuper方法表示对被代理对象方法的调用
     * @return 执行结果
     * @throws Throwable
     *             异常
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
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
}
