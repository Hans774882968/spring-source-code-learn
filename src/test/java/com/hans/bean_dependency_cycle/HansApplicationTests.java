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
