package com.hans.bean_dependency_cycle.hans;

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
