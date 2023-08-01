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
