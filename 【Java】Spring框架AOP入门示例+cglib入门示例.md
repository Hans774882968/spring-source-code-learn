[toc]

## 引言

> AOP（Aspect Oriented Programming）：面向切面编程，也叫面向方面编程，是目前软件开发中的一个热点，也是 Spring 框架中的一个重要内容。利用AOP可以对业务逻辑的各个部分进行隔离，从而使得业务逻辑各部分之间的耦合度降低，提高程序的可重用性，同时提高了开发效率。

主要使用场景：抽出多处重复的边缘业务，比如：日志记录，性能统计，安全控制，权限管理，事务处理，异常处理，资源池管理……

面向切面编程（AOP）是面向对象编程的补充，简单来说就是统一处理某一“切面”的问题的编程思想。比如说，如果使用AOP的方式进行日志的记录和处理，所有的日志代码都集中于一处，就不需要再修改所有方法，减少了重复代码。

1. 通知（Advice）包含了需要用于多个应用对象的横切行为，完全听不懂，没关系，通俗一点说就是定义了“什么时候”和“做什么”。
2. 连接点（Join Point）是程序执行过程中能够应用通知的所有点。
3. 切点（Poincut）是定义了在“什么地方”进行切入，哪些连接点会得到通知。显然，切点一定是连接点。
4. 切面（Aspect）是通知和切点的结合。通知和切点共同定义了切面的全部内容——是什么，何时，何地完成功能。
5. 引入（Introduction）允许我们向现有的类中添加新方法或者属性。
6. 织入（Weaving）是把切面应用到目标对象并创建新的代理对象的过程，分为编译期织入、类加载期织入和运行期织入。

[本文所用工程](https://github.com/Hans774882968/spring-source-code-learn)。

AOP各示例整体的文件结构：

```
├─anno
│      AOPDemoAnno.java            入口
│      AOPDemoConfig.java          配置类，用于给 Student bean 单例注入属性
│      ExecTimeAdvice.java         用于 hook
│      NewController.java          Controller 示例
│      Student.java                被 hook 的类
│
├─anno_anno
│      AOPDemoAnnoAnno.java        入口
│      AOPDemoAnnoAnnoConfig.java  配置类，用于给 Student bean 单例注入属性
│      LogAdvice.java              用于 hook
│      LogAnnotation.java          自定义注解 @LogAnnotation
│      NewController.java          Controller 示例
│      Student.java                被 hook 的类
│
└─xml
        AOPDemoXml.java            入口。引用 src\main\resources\aop_demo.xml
        Logging.java               用于 hook 的类
        Student.java               被 hook 的类
```

## AOP入门示例
### 依赖

```xml
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>1.9.19</version>
</dependency>
```

如果不加`aspectjweaver`依赖，会报错`java.lang.ClassNotFoundException: org.aspectj.lang.JoinPoint`。

### 示例1：xml配置
TODO

这个示例来自[w3schools](https://www.w3schools.cn/springaop/springaop_application.html)，我只做了类文件路径和`package`语句的修改。

文件结构：

```
├─xml
│       AOPDemoXml.java            入口。引用 src\main\resources\aop_demo.xml
│       Logging.java               用于 hook 的类
│       Student.java               被 hook 的类
└
```

xml配置格式很简单：

1. `xmlns:aop="http://www.springframework.org/schema/aop"`，`xsi:schemaLocation`新增两个url：`http://www.springframework.org/schema/aop http://www.springframework.org/schema/aop/spring-aop.xsd`。
2. 新增`<aop:config />`部分，格式很简单。

`aop_demo.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans
    xmlns="http://www.springframework.org/schema/beans"
    xmlns:aop="http://www.springframework.org/schema/aop"
    xmlns:context="http://www.springframework.org/schema/context"
    xmlns:mvc="http://www.springframework.org/schema/mvc"
    xmlns:p="http://www.springframework.org/schema/p"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
                        http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd
                        http://www.springframework.org/schema/mvc http://www.springframework.org/schema/mvc/spring-mvc.xsd
                        http://www.springframework.org/schema/aop http://www.springframework.org/schema/aop/spring-aop.xsd
                        "
>
    <aop:config>
        <aop:aspect id="log" ref="logging">
            <aop:pointcut
                id="selectAll"
                expression="execution(* com.hans.aop_demo.xml.Student.*(..))"
            />
            <aop:before pointcut-ref="selectAll" method="beforeAdvice" />
        </aop:aspect>
    </aop:config>

    <bean id="student" class="com.hans.aop_demo.xml.Student">
        <property name="name" value="hans" />
        <property name="age" value="20" />
    </bean>

    <bean id="logging" class="com.hans.aop_demo.xml.Logging" />
</beans>
```

### 示例2：`@Aspect`
TODO

文件结构：

```
├─anno
│      AOPDemoAnno.java            入口
│      AOPDemoConfig.java          配置类，用于给 Student bean 单例注入属性
│      ExecTimeAdvice.java         用于 hook
│      NewController.java          Controller 示例
│      Student.java                被 hook 的类
└
```

### 示例3：`@Aspect` + 自定义注解定位待hook的方法
TODO

文件结构：

```
├─anno_anno
│      AOPDemoAnnoAnno.java        入口
│      AOPDemoAnnoAnnoConfig.java  配置类，用于给 Student bean 单例注入属性
│      LogAdvice.java              用于 hook
│      LogAnnotation.java          自定义注解 @LogAnnotation
│      NewController.java          Controller 示例
│      Student.java                被 hook 的类
└
```

## cglib动态代理入门示例

由于静态代理需要实现目标对象的相同接口，那么可能会导致代理类非常多，不好维护，因此出现了动态代理。但JDK动态代理也有个约束：目标对象一定是要有接口的，没有接口就不能实现动态代理。因此出现了cglib代理，cglib代理也叫子类代理，通过在内存中构建出一个子类来实现hook。

根据[参考链接1](https://juejin.cn/post/7003220947465420813)，Spring框架内置的cglib包名为`org.springframework.cglib`，框架这么做的好处是，项目选用自己的cglib版本后不会和Spring框架使用的版本冲突。所以我们可以`import org.springframework.cglib.proxy.Enhancer`等。

cglib动态代码demo结构很简单，就是一个命令行程序。这个demo实现的对Student类的hook效果和上面《示例3：`@Aspect` + 自定义注解定位待hook的方法》一致。

```
├─cglib_demo
│       CglibDemo.java       入口
│       LogInterceptor.java  实现拦截逻辑
│       Student.java         被 hook 的类
└
```

`src\main\java\com\hans\cglib_demo\LogInterceptor.java`

```java
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
```

`src\main\java\com\hans\cglib_demo\Student.java`没有特别之处，不贴代码了。

`src\main\java\com\hans\cglib_demo\CglibDemo.java`

```java
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
```

最后额外给大家送上一个单文件的版本❤ `src\main\java\com\hans\cglib_demo\CglibDemoSingleFile.java`

```java
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
```

## 读取 .class 文件、动态hook+动态执行

单个文件：`src\main\java\com\hans\cglib_read_class_file\CglibDemoClassFile.java`。要点：

1. 磁盘的class文件 → `byte[]`。先获取`InputStream is`，再使用`is.read(bytes)`获取`byte[] bytes`。
2. `byte[]` → `Class<?>`。用`ClassLoader.defineClass`方法把`byte[]`转为`Class<?>`。

```java
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
```

## 参考资料

1. Spring中的Cglib代理包名为什么是org.springframework.cglib？https://juejin.cn/post/7003220947465420813