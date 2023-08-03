package com.hans.cglib_read_class_file;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

class MyClassLoader extends ClassLoader {
    public Class<?> myDefineClass(String arg0, byte[] arg1, int arg2, int arg3) {
        return super.defineClass(arg0, arg1, arg2, arg3);
    }
}

public class CglibDemoClassFile {
    static Class<?> readClassFile(String path, String classNamePath, boolean isResourceMode)
            throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        MyClassLoader cl = new MyClassLoader();
        InputStream is = isResourceMode ? cl.getResourceAsStream(path) : new FileInputStream(path);
        int len = 0;
        while (len == 0)
            len = is.available();
        byte[] b = new byte[len];
        is.read(b);
        return cl.myDefineClass(classNamePath, b, 0, b.length);
    }

    static void startHook(Class<?> targetClass) throws Exception {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(targetClass);
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy)
                    throws Throwable {
                Long startTime = System.currentTimeMillis();
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

        Object student = enhancer.create();
        Method getName = targetClass.getMethod("getName");
        Method getAge = targetClass.getMethod("getAge");
        Method setName = targetClass.getMethod("setName", String.class);
        Method setAge = targetClass.getMethod("setAge", Integer.class);
        setName.invoke(student, "hans");
        setAge.invoke(student, 14);
        for (int i = 0; i < 3; i++) {
            setAge.invoke(student, getAge.invoke(student));
        }
        System.out.println(getAge.invoke(student));
        System.out.println(getName.invoke(student));
    }

    public static void main(String[] args) throws Exception {
        Class<?> studentClass = readClassFile("Student.class", "com.hans.cglib_demo.Student", true);
        startHook(studentClass);
    }
}
