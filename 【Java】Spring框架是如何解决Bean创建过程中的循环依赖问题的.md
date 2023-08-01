[TOC]

# 【Java】Spring框架是如何解决Bean创建过程中的循环依赖问题的

## 引言

本文主要梳理了Spring框架Bean创建过程中应对循环依赖问题的相关源码。我在[手写super-mini-webpack](https://www.52pojie.cn/thread-1682010-1-1.html)的时候也介绍过解决循环依赖的算法：Map+记忆化搜索。可以猜测这段源码也实现了这个算法，所以在看这段源码的时候，我们可以先找到**递归点**，再去分析调用栈涉及的那些函数，顺便找出其用到的Map数据结构。

[本文所用工程](https://github.com/Hans774882968/spring-source-code-learn)，工程创建方式：用VSCode插件`Spring Initializr Java Support`，`ctrl + shift + P`，`Spring Initializr: Create a Maven Project`。具体看[参考链接1](https://www.cnblogs.com/miskis/p/9816135.html)。

- JDK Version：17

- SpringBoot Version：3.1.2

- Spring Version：6.0.11

**作者：[hans774882968](https://blog.csdn.net/hans774882968)以及[hans774882968](https://juejin.cn/user/1464964842528888)以及[hans774882968](https://www.52pojie.cn/home.php?mod=space&uid=1906177)**

[本文CSDN](https://blog.csdn.net/hans774882968/article/details/131996354)

[本文juejin](https://juejin.cn/post/7261064585007677499)

本文52pojie：https://www.52pojie.cn/thread-1814835-1-1.html

## 三级缓存数据结构简介

三级缓存数据结构定义和操作三级缓存的函数都位于：[spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.java](https://github.com/spring-projects/spring-framework/blob/502997d8e986dcfde1f49b2b2f443a32b5488b13/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.java)

```java
	/** Cache of singleton objects: bean name to bean instance. */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** Cache of singleton factories: bean name to ObjectFactory. */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/** Cache of early singleton objects: bean name to bean instance. */
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);
```

缓存先找到第一级，第一级没有才查第二级，依此类推。`singletonObjects, earlySingletonObjects, singletonFactories`分别是1到3级。第一级缓存是bean名到成品bean的映射；第二级缓存是bean名到**半成品bean**的映射；第三级缓存是bean名到函数式接口的映射，作用为**延迟调用**函数。

```java
@FunctionalInterface
public interface ObjectFactory<T> {

	/**
	 * Return an instance (possibly shared or independent)
	 * of the object managed by this factory.
	 * @return the resulting instance
	 * @throws BeansException in case of creation errors
	 */
	T getObject() throws BeansException;

}
```

第三级缓存的Value里的函数的调用方式就是调用`.getObject()`。

为什么需要2级缓存？因为要体现一个分层的思想，半成品bean原则上是不能暴露到外部的。TODO：外部是指？这里我没研究清楚，就先引用了52pojie`@特别的你～`大佬的解释。“防止线程切换时，其他线程取到半成品bean”，我觉得很有道理。

> 外部可能是调用spring容器中BEAN的其他参与者？有些应用启动时（如果懒加载将会是使用时）极其复杂，如果多线程调用将会出现问题（2级缓存加锁是防止多线程创建，这里指该BEAN如果没有创建好，而且只有一个容器，那么其他线程就会使用到，认为是完整的BEAN）。

为什么需要3级缓存？如果所有bean都没有代理对象就不需要第3级缓存。TODO：补充说明。

## 源码阅读

### 如何启动调试？

新建一个普通的SpringBoot项目。然后可以尝试通过xml配置文件和注解等方式构造循环引用。后文称为场景1和场景2。

#### 场景1：通过xml配置文件来构造循环引用

入口`src\main\java\com\hans\bean_dependency_cycle\HansApplication.java`：

```java
package com.hans.bean_dependency_cycle;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

@SpringBootApplication
public class HansApplication {

	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("cycle.xml");
		A beanA = ac.getBean(A.class);
		System.out.println(beanA);
		System.out.println(beanA.getB());
		B beanB = ac.getBean(B.class);
		System.out.println(beanB);
		System.out.println(beanB.getA());
	}

}
```

`src\main\resources\cycle.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans
    xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:context="http://www.springframework.org/schema/context"
    xmlns:mvc="http://www.springframework.org/schema/mvc"
    xmlns:p="http://www.springframework.org/schema/p"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
                        http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd
                        http://www.springframework.org/schema/mvc http://www.springframework.org/schema/mvc/spring-mvc.xsd
                        "
>
    <bean id="a" class="com.hans.bean_dependency_cycle.A">
        <property name="b" ref="b"></property>
    </bean>
    <bean id="b" class="com.hans.bean_dependency_cycle.B">
        <property name="a" ref="a"></property>
    </bean>
</beans>
```

`A.java`和`B.java`：

```java
// A 和 B 不能都使用 lombok，否则无法打印。这里选择了 B 不使用 lombok
package com.hans.bean_dependency_cycle;

import lombok.Data;

@Data
public class A {
    private B b;

    public String toString(A o) {
        return o.getClass().getName() + "@" +
                Integer.toHexString(System.identityHashCode(o));
    }

}

package com.hans.bean_dependency_cycle;

public class B {
    private A a;

    public A getA() {
        return this.a;
    }

    public void setA(A a) {
        this.a = a;
    }

}
```

期望输出：

```
A(b=com.hans.bean_dependency_cycle.B@750e2b97)
com.hans.bean_dependency_cycle.B@750e2b97
com.hans.bean_dependency_cycle.B@750e2b97
A(b=com.hans.bean_dependency_cycle.B@750e2b97)
```

场景1不需要配置`spring.main.allow-circular-references`为true也能得到期望输出，TODO：原因未知。

【Optional】顺便补充一下场景1的单测`src\test\java\com\hans\bean_dependency_cycleApplicationTests.java`：

```java
package com.hans.bean_dependency_cycle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

// 自动收集 cycle.xml 的 bean
@ContextConfiguration(locations = { "classpath:cycle.xml" })
@SpringBootTest(classes = HansApplication.class)
class HansApplicationTests {
	@Autowired
	ApplicationContext ac;

	@Test
	void contextLoads() {
		A beanA = ac.getBean(A.class);
		Assertions.assertNotNull(beanA);
		B beanB = ac.getBean(B.class);
		Assertions.assertNotNull(beanB);
		Assertions.assertEquals(beanA, beanB.getA());
		Assertions.assertEquals(beanB, beanA.getB());
	}

}
```

#### 场景2：通过注解+自动装配属性来构造循环引用：以`@Controller`为例

入口`src\main\java\com\hans\bean_dependency_cycle\AnotherEntry.java`：

```java
package com.hans.bean_dependency_cycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class AnotherEntry {
    public static void main(String[] args) {
        ConfigurableApplicationContext cac = SpringApplication.run(AnotherEntry.class, args);
        ControllerA beanA = cac.getBean(ControllerA.class);
        System.out.println(beanA);
        System.out.println(beanA.getCb());
        ControllerB beanB = cac.getBean(ControllerB.class);
        System.out.println(beanB);
        System.out.println(beanB.getCa());
    }
}
```

根据[参考链接2](https://juejin.cn/post/7096798740593246222)，接下来一定要记得修改`application.yml`：

```yaml
spring:
  main:
    allow-circular-references: true
```

否则会报错：

```
***************************
APPLICATION FAILED TO START
***************************

Description:

The dependencies of some of the beans in the application context form a cycle:

┌─────┐
|  controllerA (field private com.hans.bean_dependency_cycle.ControllerB com.hansn_dependency_cycle.hans.ControllerA.cb)
↑     ↓
|  controllerB (field private com.hans.bean_dependency_cycle.ControllerA com.hansn_dependency_cycle.hans.ControllerB.ca)
└─────┘


Action:

Relying upon circular references is discouraged and they are prohibited by default. Up your application to remove the dependency cycle between beans. As a last resort, it me possible to break the cycle automatically by setting spring.main.allow-circular-refees to true.
```

两个普通的Controller：

```java
// src\main\java\com\hans\bean_dependency_cycle\ControllerA.java
package com.hans.bean_dependency_cycle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerA {
    @Autowired
    private ControllerB cb;

    public ControllerB getCb() {
        return cb;
    }

    @RequestMapping("/controllerA")
    public String index() {
        return "hello controllerA!";
    }
}

// src\main\java\com\hans\bean_dependency_cycle\ControllerB.java
package com.hans.bean_dependency_cycle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerB {
    @Autowired
    private ControllerA ca;

    public ControllerA getCa() {
        return ca;
    }

    @RequestMapping("/controllerB")
    public String index() {
        return "hello controllerB!";
    }
}
```

期望输出：

```
com.hans.bean_dependency_cycle.ControllerA@39f82681
com.hans.bean_dependency_cycle.ControllerB@4bd9e7fd
com.hans.bean_dependency_cycle.ControllerB@4bd9e7fd
com.hans.bean_dependency_cycle.ControllerA@39f82681
```

【Optional】顺便补充一下场景2的单测`src\test\java\com\hans\bean_dependency_cycle\AnotherEntryTests.java`

```java
package com.hans.bean_dependency_cycle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AnotherEntry.class)
class AnotherEntryTests {
	@Autowired
	ControllerA ca;

	@Autowired
	ControllerB cb;

	@Test
	void contextLoads() {
		Assertions.assertNotNull(ca);
		Assertions.assertNotNull(cb);
		Assertions.assertEquals(ca, cb.getCa());
		Assertions.assertEquals(cb, ca.getCb());
	}

}
```

### 脉络

根据[参考链接3](https://www.bilibili.com/video/BV1J14y1A7eE/?p=82)，有一个经验：“do”开头的方法名是真正含有大量逻辑的方法。参考链接3~~Java之父精选的~~脉络函数如下：

- `getBean`
- `doGetBean`
- `createBean`
- `doCreateBean`
- `createBeanInstance`
- `populateBean`

场景1到递归点为止的调用链：`preInstantiateSingletons > getBean > doGetBean > getSingleton函数有多个，其中调用了beforeSingletonCreation的函数调用singletonFactory.getObject()时才真正调用了createBean > createBean > doCreateBean > createBeanInstance, populateBean 都在 doCreateBean 的实现里 > populateBean调用了applyPropertyValues > resolveValueIfNecessary > resolveReference > getBean`。

场景2的调用链到`populateBean`开始和场景1的调用链岔开，两个场景的差异放在后文《Controller和普通Bean解决循环依赖过程的相同点与不同点》分析。

场景1递归到`A -> B -> A`时的调用链：`getBean > doGetBean > 未调用beforeSingletonCreation的getSingleton，操作第2级缓存后离开`。

操作三级缓存的函数都位于`https://github.com/spring-projects/spring-framework/blob/502997d8e986dcfde1f49b2b2f443a32b5488b13/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.java`：

1. 放入第3级缓存的函数：`doCreateBean`调用的`addSingletonFactory`。
2. 放入第2级缓存的函数：首先要知道`getSingleton`在`DefaultSingletonBeanRegistry.java`里本质上的实现有两个，一个`public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory)`调用了`beforeSingletonCreation`并间接调用了`createBean`；另一个`protected Object getSingleton(String beanName, boolean allowEarlyReference)`则是操作了第2级缓存。
3. 放入第1级缓存的函数：`addSingleton`。在间接调用了`createBean`函数的`getSingleton`处调用。

### 场景1的执行过程

真正意义上的入口：[spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java](https://github.com/spring-projects/spring-framework/blob/3a481a7d7f7ee485e25003b2855b4379ab5cce3a/spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java)的`beanFactory.preInstantiateSingletons();`。这里的`beanFactory`就有`singletonObjects`那3级缓存的对象。于是跳到`https://github.com/spring-projects/spring-framework/blob/bbde68c49e66c3c531920cb80a55742262507be7/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultListableBeanFactory.java`：

```java
	@Override
	public void preInstantiateSingletons() throws BeansException {
		if (logger.isTraceEnabled()) {
			logger.trace("Pre-instantiating singletons in " + this);
		}

		// Iterate over a copy to allow for init methods which in turn register new bean definitions.
		// While this may not be part of the regular factory bootstrap, it does otherwise work fine.
		List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

        // 触发所有非延迟加载的（non-lazy）单例 bean 的初始化
		// Trigger initialization of all non-lazy singleton beans...
		for (String beanName : beanNames) {
			// 假如先遍历 A 再遍历 B 那么遍历到 B 的时候，因为循环引用解决的关系，B 已经放到了第1级缓存，所以 doGetBean 的 getSingleton 可以直接从第1级缓存取到值，不用再走一遍 createBean 方法
            // 合并父类 BeanDefinition
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            // 非抽象、是单例、非懒加载
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                // 如果实现了 FactoryBean 接口则是 FactoryBean
				if (isFactoryBean(beanName)) {
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName); // 比如：FACTORY_BEAN_PREFIX + beanName = "&A"
					if (bean instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isEagerInit()) {
						getBean(beanName);
					}
				}
				else {
                    // 不是 FactoryBean，只是普通 Bean，则走这个分支
					getBean(beanName);
				}
			}
		}

        // 触发所有 SmartInitializingSingleton 的后初始化回调
		// Trigger post-initialization callback for all applicable beans...
		for (String beanName : beanNames) {
			Object singletonInstance = getSingleton(beanName);
			if (singletonInstance instanceof SmartInitializingSingleton smartSingleton) {
				StartupStep smartInitialize = getApplicationStartup().start("spring.beans.smart-initialize")
						.tag("beanName", beanName);
				smartSingleton.afterSingletonsInstantiated();
				smartInitialize.end();
			}
		}
	}
```

顺便看下`isFactoryBean`的实现`https://github.com/spring-projects/spring-framework/blob/4786e2bf53a3f882c10e25d7ff79a18ff47b5e51/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java`：

```java
	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);
		}
		// No singleton instance found -> check bean definition.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory cbf) {
			// No bean definition found in this factory -> delegate to parent.
			return cbf.isFactoryBean(name);
		}
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}
```

`getBean`和`isFactoryBean`都位于`https://github.com/spring-projects/spring-framework/blob/4786e2bf53a3f882c10e25d7ff79a18ff47b5e51/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java`，`getBean`只有1行

```java
	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}
```

`doGetBean`和`getBean, isFactoryBean`都在`AbstractBeanFactory.java`：

```java
	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @param typeCheckOnly whether the instance is obtained for a type check,
	 * not for actual use
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {

		String beanName = transformedBeanName(name);
		Object beanInstance;

        // 提前检查单例缓存中是否有手动注册的单例对象，跟循环依赖有关。如果是初次进这里，比如 controllerA 初次进这个方法，肯定是拿不到值的，就会是 null
		// 对于最简单的循环依赖，controllerA -> controllerB -> controllerA 之后，需要进入 getSingleton 了，这里的逻辑就是要从第3级缓存里拿到工厂函数，调用后得到 controllerA 半成品，从而可以直接 return
		// Eagerly check singleton cache for manually registered singletons.
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
			// Fail if we're already creating this bean instance:
			// We're assumably within a circular reference.
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// Check if bean definition exists in this factory.
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// Not found -> check parent.
				String nameToLookup = originalBeanName(name);
				if (parentBeanFactory instanceof AbstractBeanFactory abf) {
					return abf.doGetBean(nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					// Delegation to parent with explicit args.
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method.
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			if (!typeCheckOnly) {
				markBeanAsCreated(beanName);
			}

			StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate")
					.tag("beanName", name);
			try {
				if (requiredType != null) {
					beanCreation.tag("beanType", requiredType::toString);
				}
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				checkMergedBeanDefinition(mbd, beanName, args);

				// Guarantee initialization of beans that the current bean depends on.
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						registerDependentBean(dep, beanName);
						try {
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				// Create bean instance.
				if (mbd.isSingleton()) {
					// 返回 beanName 的原始单例对象。如果尚未注册，则使用 singletonFactory 创建并注册一个对象
					sharedInstance = getSingleton(beanName, () -> {
						try {
							// 为给定的合并后的 BeanDefinition 和参数创建一个 bean 实例
							// createBean 真实执行时机是调用了 beforeSingletonCreation 方法的 getSingleton 方法执行 singletonObject = singletonFactory.getObject() 时
							// 首次运行到 beanName = "controllerA" 时 args = null
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							// 显式地从单例缓存中删除bean实例：因为这个实例可能是由创建过程急切地放在那里的，放在那里的目的是允许循环引用解析。
							// 还要删除所有被这个bean临时引用的所有bean。如果找到相应的一次性bean实例，则委托给 destroyBean
							destroySingleton(beanName);
							throw ex;
						}
					});
					beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					Object prototypeInstance = null;
					try {
						beforePrototypeCreation(beanName);
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						afterPrototypeCreation(beanName);
					}
					beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}

				else {
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean '" + beanName + "'");
					}
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						Object scopedInstance = scope.get(beanName, () -> {
							beforePrototypeCreation(beanName);
							try {
								return createBean(beanName, mbd, args);
							}
							finally {
								afterPrototypeCreation(beanName);
							}
						});
						beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						throw new ScopeNotActiveException(beanName, scopeName, ex);
					}
				}
			}
			catch (BeansException ex) {
				beanCreation.tag("exception", ex.getClass().toString());
				beanCreation.tag("message", String.valueOf(ex.getMessage()));
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
			finally {
				beanCreation.end();
			}
		}

		return adaptBeanInstance(name, beanInstance, requiredType);
	}
```

我们先只关注`createBean`，所以需要关注`getSingleton`。注意：

> `getSingleton`的函数在`DefaultSingletonBeanRegistry.java`里本质上的实现有两个，一个调用了`beforeSingletonCreation`并间接调用了`createBean`；另一个则是操作了第2级缓存。

所以目前我们需要关注的是调用了`beforeSingletonCreation`的`getSingleton`方法。路径：`https://github.com/spring-projects/spring-framework/blob/502997d8e986dcfde1f49b2b2f443a32b5488b13/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.java`

```java
	/**
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		synchronized (this.singletonObjects) {
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				// 创建单例前的回调，默认实现为：将单例注册为当前正在创建中，实现只有3行，可以看下。
				beforeSingletonCreation(beanName);
				// flag 表示是否生成了新的单例对象
				boolean newSingleton = false;
				// flag 表示是否有抑制异常的记录，true表示没有
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					// 若没有抑制异常记录，则对抑制异常列表进行初始化
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					// 从单例工厂获取对象。注意 singletonFactory 是本方法第二个参数，之前也介绍了
					// ObjectFactory 对象通过调 getObject 来正式执行函数，所以 createBean 在此时才真正执行
					singletonObject = singletonFactory.getObject();
					// 获取后，就已经生成了新的单例对象，标记为 true
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					// 操作第1级缓存
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}
```

在调用`singletonObject = singletonFactory.getObject();`以间接调用`createBean`后，会调用`addSingleton`，将bean加入第1级缓存，这标志着bean变为成品。接下来我们看下`addSingleton`的代码，它和`getSingleton`定义于同一个文件。

```java
	/**
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		// addSingleton 在调用了 createBean 的 getSingleton 方法中被调用，标志着 bean 变为成品对象
		synchronized (this.singletonObjects) {
			// 第1级缓存添加，第2、3级缓存移除
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			// 添加到已注册的单例集合。 registeredSingletons 为 Set<String>
			this.registeredSingletons.add(beanName);
		}
	}
```

接下来看`createBean`。`https://github.com/spring-projects/spring-framework/blob/4786e2bf53a3f882c10e25d7ff79a18ff47b5e51/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java`

```java
	/**
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * @see #doCreateBean
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {
		// 首次看源码，直接看 doCreateBean 调用处
		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		try {
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
			// 实际创建 bean
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}
```

`createBean`目前唯一值得关注的点就是调用了`doCreateBean`。`doCreateBean`做了几件值得本文关注的事：

- 调用了`createBeanInstance`完成bean的实例化。
- 调用了`addSingletonFactory`，即加入了第3级缓存。
- 调用了`populateBean`完成bean的属性赋值操作。

`doCreateBean`和`createBean`都定义在`https://github.com/spring-projects/spring-framework/blob/4786e2bf53a3f882c10e25d7ff79a18ff47b5e51/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java`。

```java
	/**
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 */
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		// instanceWrapper 持有创建出的 bean 对象
		BeanWrapper instanceWrapper = null;
		// 获取 factoryBean 实例缓存
		if (mbd.isSingleton()) {
			// 如果是单例对象，从 factoryBean 实例缓存中移除当前 bean 的信息
			// 首次 controllerA 进来会进行移除操作，并且会调用 createBeanInstance
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		// 没有就创建实例
		if (instanceWrapper == null) {
			// 根据执行 bean 使用对应的策略创建新的工厂实例，如：工厂方法、构造函数主动注入、简单初始化
			// 第一次读源码时不需要点进去看 createBeanInstance 。下一个主干方法 populateBean 是在本函数下文调用的
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		// 从包装类中获取原始 bean 。首次执行 controllerA 的时候, bean 就是普通的 controllerA 对象 ControllerA@100{cb=null}
		Object bean = instanceWrapper.getWrappedInstance();
		// 获取具体 bean 对象的 Class 属性
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			// 不等于 NullBean 类型时就修改目标类型
			mbd.resolvedTargetType = beanType;
		}

		// Allow post-processors to modify the merged bean definition.
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.markAsPostProcessed();
			}
		}

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		// 判断当前 bean 是否需要提前曝光，条件为：是单例 && 允许循环依赖 spring.main.allow-circular-references 配置为 true && 当前 bean 正在创建中
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			// 为避免后期循环依赖，可以在 bean 初始化完成前将创建实例的 ObjectFactory 加入工厂
			// 注意，这个方法会操作3级缓存的数据结构，尤其是第3级缓存。在 controllerA -> controllerB -> controllerA 的时候未调用 createBean 的 getSingleton 方法会真正调用这个匿名函数，从而调用 getEarlyBeanReference 进而操作第2级缓存
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

		// Initialize the bean instance.
		// controllerA 首次执行到这里时，exposedObject = ControllerA@100{cb=null}
		Object exposedObject = bean;
		try {
			// populateBean 是主干方法，给刚刚实例化的 bean （半成品）填充属性
			populateBean(beanName, mbd, instanceWrapper);
			// controllerA -> controllerB -> controllerA 之后，getSingleton 返回 controllerA 后回到这里， controllerB 的 ca 属性就有值了
			// 随后递归返回到这句话且调用栈只有 controllerA 的时候，发现两者都有值了
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException bce && beanName.equals(bce.getBeanName())) {
				throw bce;
			}
			else {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
			}
		}

		if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		try {
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}
```

`createBeanInstance`的细节与本文主题无关，不关注。接下来我们看下`addSingletonFactory`的实现。`addSingletonFactory`位于`https://github.com/spring-projects/spring-framework/blob/502997d8e986dcfde1f49b2b2f443a32b5488b13/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.java`，主要动作是操作第3级缓存。

```java
	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			// controllerA 首次执行到这里，第一级缓存肯定是查不到的
			if (!this.singletonObjects.containsKey(beanName)) {
				// 第3级缓存放入。再回顾一下， singletonFactory存的是 beanName 到一个延迟执行的函数的映射
				// controllerA 首次执行到这里的时候， singletonFactory = () -> getEarlyBeanReference(beanName, mbd, bean)
				this.singletonFactories.put(beanName, singletonFactory);
				// 从早期单例对象的高速缓存（即第2级缓存）移除当前 beanName 对应的缓存对象
				this.earlySingletonObjects.remove(beanName);
				// 添加到已注册的单例集合里，和三级缓存无关。值得注意的是， A 首次加入三级缓存时，就是首次加入已注册的单例集合
				this.registeredSingletons.add(beanName);
			}
		}
	}
```

接下来关注位于`https://github.com/spring-projects/spring-framework/blob/4786e2bf53a3f882c10e25d7ff79a18ff47b5e51/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java`的`populateBean`。`populateBean`方法有一个功能是给bean的属性赋值，包含了递归点。在创建Bean的源码中，递归点指的是递归调用`getBean`方法。

```java
	/**
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw the BeanWrapper with bean instance
	 */
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				return;
			}
		}

		if (bw.getWrappedClass().isRecord()) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to a record");
			}
			else {
				// Skip property population phase for records since they are immutable.
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					return;
				}
			}
		}

		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// Add property values based on autowire by name if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}
		if (hasInstantiationAwareBeanPostProcessors()) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				// 对于 Controller Bean 自动装配属性产生循环依赖的场景，遍历到 AutowiredAnnotationBeanPostProcessor 时，这句话包含递归点
				PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
				if (pvsToUse == null) {
					return;
				}
				pvs = pvsToUse;
			}
		}

		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);
		if (needsDepCheck) {
			PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
			// 对于普通 Bean 的场景，这句话包含递归点
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}
```

`applyPropertyValues`也位于`https://github.com/spring-projects/spring-framework/blob/4786e2bf53a3f882c10e25d7ff79a18ff47b5e51/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java`，主要功能为完成`bean`初始化。其中，`Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue)`包含了递归点，通过动调可以验证`bw.setPropertyValues(new MutablePropertyValues(deepCopy))`真正完成了属性赋值工作。

```java
	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * @param beanName the bean name passed for better exception information
	 * @param mbd the merged bean definition
	 * @param bw the BeanWrapper wrapping the target object
	 * @param pvs the new property values
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		// applyPropertyValues 是真正完成赋值操作的函数
		// 如果 pvs 没有 PropertyValue，就直接结束
		if (pvs.isEmpty()) {
			return;
		}

		MutablePropertyValues mpvs = null;
		List<PropertyValue> original;

		if (pvs instanceof MutablePropertyValues _mpvs) {
			mpvs = _mpvs;
			if (mpvs.isConverted()) {
				// Shortcut: use the pre-converted values as-is.
				try {
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			// 获取 mpvs 的 PropertyValue 列表
			original = mpvs.getPropertyValueList();
		}
		else {
			// 获取 pvs 的 PropertyValue 对象数组并转为列表
			original = Arrays.asList(pvs.getPropertyValues());
		}

		// 用户自定义的类型转换器，默认转换器为 bean 的包装类对象
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		// Create a deep copy, resolving any references for values.
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		// resolveNecessary flag 含义为是否还需要解析
		boolean resolveNecessary = false;
		for (PropertyValue pv : original) {
			// 属性已解析过则加入 deepCopy
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			else {
				String propertyName = pv.getName();
				// 获取未经类型转换的值
				Object originalValue = pv.getValue();
				if (originalValue == AutowiredPropertyMarker.INSTANCE) {
					Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
					// 如果 setter 方法为空
					if (writeMethod == null) {
						// 异常：自动装配标记属性没有写方法
						throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
					}
					originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
				}
				// 交由 valueResolver 根据 pv 解析出 originalValue 所封装的对象。注意：这个函数包含递归点
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				Object convertedValue = resolvedValue;
				// flag 含义为是否可转换： propertyName 是 bw 中的可写属性 && propertyName 不是索引属性或嵌套属性
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {
					// 可转换则将 resolvedValue 转换为指定的目标属性对象
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}
				else if (convertible && originalValue instanceof TypedStringValue typedStringValue &&
						!typedStringValue.isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		// Set our (possibly massaged) deep copy.
		try {
			// 完成属性赋值工作。咱们做个简单的实验，动调执行此句前后各点击调用栈看一次 populateBean 调用处的下一句的 exposedObject 或者 bean 变量，这就证实了 bean 的属性赋值确实是在这句话完成的
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
		}
	}
```

`resolveValueIfNecessary`位于`https://github.com/spring-projects/spring-framework/blob/30d6ec3398ce41add7bc44d360b8fb86ac0264b1/spring-beans/src/main/java/org/springframework/beans/factory/support/BeanDefinitionValueResolver.java`。其实现也很长，但目前只需要关注`return resolveReference(argName, ref)`，因为`resolveReference`包含递归点。

```java
	/**
	 * Given a PropertyValue, return a value, resolving any references to other
	 * beans in the factory if necessary. The value could be:
	 * <li>A BeanDefinition, which leads to the creation of a corresponding
	 * new bean instance. Singleton flags and names of such "inner beans"
	 * are always ignored: Inner beans are anonymous prototypes.
	 * <li>A RuntimeBeanReference, which must be resolved.
	 * <li>A ManagedList. This is a special collection that may contain
	 * RuntimeBeanReferences or Collections that will need to be resolved.
	 * <li>A ManagedSet. May also contain RuntimeBeanReferences or
	 * Collections that will need to be resolved.
	 * <li>A ManagedMap. In this case the value may be a RuntimeBeanReference
	 * or Collection that will need to be resolved.
	 * <li>An ordinary object or {@code null}, in which case it's left alone.
	 * @param argName the name of the argument that the value is defined for
	 * @param value the value object to resolve
	 * @return the resolved object
	 */
	@Nullable
	public Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
		// We must check each value to see whether it requires a runtime reference
		// to another bean to be resolved.
		// RuntimeBeanReference：当属性值对象是工厂中另一个 bean 的引用时，使用不可变的占位符类，在运行时进行解析
		if (value instanceof RuntimeBeanReference ref) {
			// 解析出对应 ref 所封装的 Bean 的元信息的 Bean 对象。Bean 的元信息：Bean 名，Bean类型。注意，这个函数里包含递归点
			return resolveReference(argName, ref);
		}
		else if (value instanceof RuntimeBeanNameReference ref) {
			String refName = ref.getBeanName();
			refName = String.valueOf(doEvaluate(refName));
			if (!this.beanFactory.containsBean(refName)) {
				throw new BeanDefinitionStoreException(
						"Invalid bean name '" + refName + "' in bean reference for " + argName);
			}
			return refName;
		}
		else if (value instanceof BeanDefinitionHolder bdHolder) {
			// Resolve BeanDefinitionHolder: contains BeanDefinition with name and aliases.
			return resolveInnerBean(bdHolder.getBeanName(), bdHolder.getBeanDefinition(),
					(name, mbd) -> resolveInnerBeanValue(argName, name, mbd));
		}
		else if (value instanceof BeanDefinition bd) {
			return resolveInnerBean(null, bd,
					(name, mbd) -> resolveInnerBeanValue(argName, name, mbd));
		}
		else if (value instanceof DependencyDescriptor dependencyDescriptor) {
			Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
			Object result = this.beanFactory.resolveDependency(
					dependencyDescriptor, this.beanName, autowiredBeanNames, this.typeConverter);
			for (String autowiredBeanName : autowiredBeanNames) {
				if (this.beanFactory.containsBean(autowiredBeanName)) {
					this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
				}
			}
			return result;
		}
		else if (value instanceof ManagedArray managedArray) {
			// May need to resolve contained runtime references.
			Class<?> elementType = managedArray.resolvedElementType;
			if (elementType == null) {
				String elementTypeName = managedArray.getElementTypeName();
				if (StringUtils.hasText(elementTypeName)) {
					try {
						elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
						managedArray.resolvedElementType = elementType;
					}
					catch (Throwable ex) {
						// Improve the message by showing the context.
						throw new BeanCreationException(
								this.beanDefinition.getResourceDescription(), this.beanName,
								"Error resolving array type for " + argName, ex);
					}
				}
				else {
					elementType = Object.class;
				}
			}
			return resolveManagedArray(argName, (List<?>) value, elementType);
		}
		else if (value instanceof ManagedList<?> managedList) {
			// May need to resolve contained runtime references.
			return resolveManagedList(argName, managedList);
		}
		else if (value instanceof ManagedSet<?> managedSet) {
			// May need to resolve contained runtime references.
			return resolveManagedSet(argName, managedSet);
		}
		else if (value instanceof ManagedMap<?, ?> managedMap) {
			// May need to resolve contained runtime references.
			return resolveManagedMap(argName, managedMap);
		}
		else if (value instanceof ManagedProperties original) {
			// Properties original = managedProperties;
			Properties copy = new Properties();
			original.forEach((propKey, propValue) -> {
				if (propKey instanceof TypedStringValue typedStringValue) {
					propKey = evaluate(typedStringValue);
				}
				if (propValue instanceof TypedStringValue typedStringValue) {
					propValue = evaluate(typedStringValue);
				}
				if (propKey == null || propValue == null) {
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"Error converting Properties key/value pair for " + argName + ": resolved to null");
				}
				copy.put(propKey, propValue);
			});
			return copy;
		}
		else if (value instanceof TypedStringValue typedStringValue) {
			// Convert value to target type here.
			Object valueObject = evaluate(typedStringValue);
			try {
				Class<?> resolvedTargetType = resolveTargetType(typedStringValue);
				if (resolvedTargetType != null) {
					return this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType);
				}
				else {
					return valueObject;
				}
			}
			catch (Throwable ex) {
				// Improve the message by showing the context.
				throw new BeanCreationException(
						this.beanDefinition.getResourceDescription(), this.beanName,
						"Error converting typed String value for " + argName, ex);
			}
		}
		else if (value instanceof NullBean) {
			return null;
		}
		else {
			return evaluate(value);
		}
	}
```

`resolveReference`和`resolveValueIfNecessary`都位于`https://github.com/spring-projects/spring-framework/blob/30d6ec3398ce41add7bc44d360b8fb86ac0264b1/spring-beans/src/main/java/org/springframework/beans/factory/support/BeanDefinitionValueResolver.java`。其实现也很长，但目前只需要关注`bean = this.beanFactory.getBean(resolvedName)`，因为这就是递归点。

```java
	/**
	 * Resolve a reference to another bean in the factory.
	 */
	@Nullable
	private Object resolveReference(Object argName, RuntimeBeanReference ref) {
		try {
			Object bean;
			Class<?> beanType = ref.getBeanType();
			// 如果引用来自父工厂
			if (ref.isToParent()) {
				// 获取父工厂
				BeanFactory parent = this.beanFactory.getParentBeanFactory();
				if (parent == null) {
					// 没有父工厂则报错：在父工厂中无法解析对 Bean 的引用，因为父工厂就不存在
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"Cannot resolve reference to bean " + ref +
									" in parent factory: no parent factory available");
				}
				if (beanType != null) {
					bean = parent.getBean(beanType);
				}
				else {
					bean = parent.getBean(String.valueOf(doEvaluate(ref.getBeanName())));
				}
			}
			else {
				String resolvedName;
				if (beanType != null) {
					// 解析与 beanType 唯一匹配的 bean 实例，包括其 bean 名
					NamedBeanHolder<?> namedBean = this.beanFactory.resolveNamedBean(beanType);
					// 让 bean 引用 namedBean 所封装的 bean 对象
					bean = namedBean.getBeanInstance();
					resolvedName = namedBean.getBeanName();
				}
				else {
					resolvedName = String.valueOf(doEvaluate(ref.getBeanName()));
					// 获取 ref 所包装的 Bean 名对应的 Bean 对象
					// 注意，这就是递归点了
					bean = this.beanFactory.getBean(resolvedName);
				}
				// 注册依赖关系到 Bean 工厂
				this.beanFactory.registerDependentBean(resolvedName, this.beanName);
			}
			if (bean instanceof NullBean) {
				bean = null;
			}
			return bean;
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					this.beanDefinition.getResourceDescription(), this.beanName,
					"Cannot resolve reference to bean '" + ref.getBeanName() + "' while setting " + argName, ex);
		}
	}
```

`B`创建过程调用栈是完全一样的，接下来我们假设现在走到了`A -> B -> A`，回到了`doGetBean`的`Object sharedInstance = getSingleton(beanName);`处。此时我们需要关注其实现了，因为这个函数要调用第3级缓存的函数，获取半成品bean，并放入第2级缓存。

```java
	/**
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// Quick check for existing instance without full singleton lock
		// 该函数主要是调用放入第3级缓存的 getEarlyBeanReference 并放入第2级缓存
		// 首先从第1级缓存获取 bean
		Object singletonObject = this.singletonObjects.get(beanName);
		// 第1级缓存没有，并且已标记为创建中
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 从第2级缓存获取 bean.因为controllerA -> controllerB -> controllerA 的时候是创建中的 bean,只放到了第3级缓存,所以是查不到的
			singletonObject = this.earlySingletonObjects.get(beanName);
			// controllerA -> controllerB -> controllerA 的时候进来, allowEarlyReference 肯定是 true
			if (singletonObject == null && allowEarlyReference) {
				synchronized (this.singletonObjects) {
					// Consistent creation of early reference within full singleton lock
					// 这段做二次确认的代码让我联想到线程安全的单例模式的写法
					// 从第1级缓存取
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						// 从第2级缓存取
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							// 从第3级缓存取。如果是 controllerA 初次进来，因为 not in creation 所以不会进这里，就算进了这里，因为第3级缓存取不到所以还是会直接 return。如果是 controllerA -> controllerB -> controllerA 则会操作第2级缓存
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								// 回顾一下，DefaultSingletonBeanRegistry.java 的 addSingletonFactory 函数在操作第3级缓存的时候，放入的匿名函数就是：
								// singletonFactory = () -> getEarlyBeanReference(beanName, mbd, bean) 所以 getEarlyBeanReference 返回值会在此被放入第2级缓存
								singletonObject = singletonFactory.getObject();
								// 为什么是放第2级缓存？因为 getBean 的递归还没返回
								// 放入第2级缓存后，第3级缓存的就可以移除了
								this.earlySingletonObjects.put(beanName, singletonObject);
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		return singletonObject;
	}
```

再回忆一遍，什么时候加入第1级缓存？调用了`beforeSingletonCreation`的`getSingleton`方法在间接调用`createBean`后，会调用`addSingleton`方法，将成品bean加入第1级缓存。

`A -> B -> A`的递归返回后，`A, B`两个单例bean都已经是成品，`beanName`遍历到`B`的时候，进入`getSingleton`就能命中第1级缓存了，不用再走一遍`createBean`方法。

```java
		for (String beanName : beanNames) {
			// 假如先遍历 A 再遍历 B 那么遍历到 B 的时候，因为循环引用解决的关系，B 已经放到了第1级缓存，所以 doGetBean 的 getSingleton 可以直接从第1级缓存取到值，不用再走一遍 createBean 方法
            // 合并父类 BeanDefinition
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            // 非抽象、是单例、非懒加载
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                // 如果实现了 FactoryBean 接口则是 FactoryBean
				if (isFactoryBean(beanName)) {
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName); // 比如：FACTORY_BEAN_PREFIX + beanName = "&A"
					if (bean instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isEagerInit()) {
						getBean(beanName);
					}
				}
				else {
                    // 不是 FactoryBean，只是普通 Bean，则走这个分支
					getBean(beanName);
				}
			}
		}
```

经过上面的分析，我们来看更简单的情况：如果没有循环依赖，比如只有`A`依赖`B`，对三级缓存数据结构的操作是怎样的？梳理出调用栈如下：`A doCreateBean -> A applyPropertyValues -> B doGetBean -> B doCreateBean -> B applyPropertyValues -> B回到getSingleton调用addSingleton -> A addSingleton`。可见如果没有循环依赖，就不需要操作第2、3级缓存，但仍然会操作第1级缓存。

至此，场景1的递归点和三级缓存的操作时机都已经清楚了。

### `@Controller`+自动装配属性和普通Bean解决循环依赖过程的相同点与不同点

场景2的Controller使用了`@Autowired`注解来构造循环依赖。动调可知，这个场景并不是在`populateBean`的`applyPropertyValues(beanName, mbd, bw, pvs);`完成修改的，而是在`populateBean`的：

```java
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
				if (pvsToUse == null) {
					return;
				}
				pvs = pvsToUse;
			}
```

这个循环里完成属性赋值的。动调发现`getBeanPostProcessorCache().instantiationAware`有一个元素是`AutowiredAnnotationBeanPostProcessor@133`（所有元素分别是`ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor@129, InfrastructureAdvisorAutoProxyCreator@130, PersistenceExceptionTranslationPostProcessor@131, CommonAnnotationBeanPostProcessor@132, AutowiredAnnotationBeanPostProcessor@133`），遍历到这个元素时执行的操作完成了自动装配属性的赋值。那我们跟进去看下。

`AutowiredAnnotationBeanPostProcessor.postProcessProperties`位于`https://github.com/spring-projects/spring-framework/blob/6183f0684684912802021556dce916ba26228c26/spring-beans/src/main/java/org/springframework/beans/factory/annotation/AutowiredAnnotationBeanPostProcessor.java`

```java
	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
		try {
			metadata.inject(bean, beanName, pvs);
		}
		catch (BeanCreationException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
		}
		return pvs;
	}
```

显然是在`metadata.inject`处完成自动装配的。`InjectionMetadata.inject`位于`https://github.com/spring-projects/spring-framework/blob/2f33e77ab49f136d83b6ebf5eeb72d200fe23c0b/spring-beans/src/main/java/org/springframework/beans/factory/annotation/InjectionMetadata.java`

```java
	public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
		Collection<InjectedElement> checkedElements = this.checkedElements;
		Collection<InjectedElement> elementsToIterate =
				(checkedElements != null ? checkedElements : this.injectedElements);
		if (!elementsToIterate.isEmpty()) {
			for (InjectedElement element : elementsToIterate) {
				element.inject(target, beanName, pvs);
			}
		}
	}
```

`element.inject`最终跳入的是`https://github.com/spring-projects/spring-framework/blob/6183f0684684912802021556dce916ba26228c26/spring-beans/src/main/java/org/springframework/beans/factory/annotation/AutowiredAnnotationBeanPostProcessor.java`的`protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable`。`AutowiredFieldElement, AutowiredMethodElement`都有`inject`方法，显然这个case里我们调用的是`AutowiredFieldElement`的`inject`方法。

```java
		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			Field field = (Field) this.member;
			Object value;
			if (this.cached) {
				try {
					value = resolveCachedArgument(beanName, this.cachedFieldValue);
				}
				catch (BeansException ex) {
					// Unexpected target bean mismatch for cached argument -> re-resolve
					this.cached = false;
					logger.debug("Failed to resolve cached argument", ex);
					value = resolveFieldValue(field, bean, beanName);
				}
			}
			else {
				value = resolveFieldValue(field, bean, beanName);
			}
			if (value != null) {
				ReflectionUtils.makeAccessible(field);
				field.set(bean, value);
			}
		}
```

显然`field.set(bean, value);`最终完成了属性的自动装配。值得注意的是，动调看到`resolveFieldValue`获取到的`cb`是已经装配好的，这里一定存在递归调用。我们用一个简单的动态调试技巧来找到递归点：在执行到`value = resolveFieldValue(field, bean, beanName)`时，给`doGetBean`函数下一个临时的断点。得到的调用栈如下：

1. `AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement.inject()`
2. `AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement.resolveFieldValue()`的`value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);`
3. `DefaultListableBeanFactory.java`的`resolveDependency`方法的`result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);`
4. `DefaultListableBeanFactory.java`的`doResolveDependency`方法的`instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);`
5. `https://github.com/spring-projects/spring-framework/blob/f1fe16e3cda66b164f77489f82287116477197bc/spring-beans/src/main/java/org/springframework/beans/factory/config/DependencyDescriptor.java`的`resolveCandidate`方法的`return beanFactory.getBean(beanName);`，这就是递归点了，`beanName = controllerB`。

总而言之，Controller的bean自动装配属性的场景和普通的bean的递归点不一样，但对三级缓存的操作逻辑是完全一致的。谜底已揭晓~

## 一些扩展结论

【1】为什么Spring不能解决构造器的循环依赖？

在`doCreateBean`调用`createBeanInstance`时，一二三级缓存都没有Bean的相关信息，在实例化之后才调用`addSingletonFactory`放入第3级缓存中，因此当getBean的时候缓存不会命中，所以会抛出循环依赖的异常。

【2】为什么多实例Bean不能解决循环依赖？

多实例Bean是每次创建都会调用`doGetBean`方法，`mbd.isSingleton()`是false，不走`sharedInstance = getSingleton(beanName, () -> {`这个分支，所以也不会使用三级缓存，不能解决循环依赖。

## 总结

1. 根据常识猜测Spring创建Bean过程解决循环依赖的算法也是Map+记忆化搜索。所以我们可以先找到**递归点**，再去分析调用栈涉及的那些函数，顺便找出其用到的Map数据结构。
2. 在不了解Spring框架的情况下可以用一个动态调试技巧快速找到递归点：在执行到某条顺序靠后的语句时，给顺序靠前的语句下一个断点，若下断成功，则说明找到了递归点。剩下的工作就是关注调用栈涉及的函数。

## 参考资料

1. VSCode搭建Java开发环境（SpringBoot项目创建、运行、调试）：https://www.cnblogs.com/miskis/p/9816135.html
2. 痛快！SpringBoot终于禁掉了循环依赖！https://juejin.cn/post/7096798740593246222
3. https://www.bilibili.com/video/BV1J14y1A7eE/?p=82 ~~震惊！这位零基础前端同学在学习Java的第7天就可以研究Spring框架源码了！这是怎么做到的呢？噢！原来是站在了Java之父🐎士兵的肩膀上~~！