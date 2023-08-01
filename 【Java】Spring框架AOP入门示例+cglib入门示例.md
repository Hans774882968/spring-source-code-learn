[toc]

## 引言
TODO

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

## 参考资料

1. Spring中的Cglib代理包名为什么是org.springframework.cglib？https://juejin.cn/post/7003220947465420813