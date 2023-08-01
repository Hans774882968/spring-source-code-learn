package com.hans.aop_demo.anno_anno;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.Data;

// 直接返 Student 无法序列化，所以新写一个 StudentResp 类
@AllArgsConstructor
@Data
class StudentResp {
    private Integer age;
    private String name;
}

@RestController
@RequestMapping("/aopDemo/annoAnno")
public class NewController {
    @Autowired
    ConfigurableApplicationContext cac;

    @LogAnnotation
    @RequestMapping("")
    public String index() throws InterruptedException {
        Thread.sleep(10);
        return "AOP 示例：通过自定义 Annotation 指定要 hook 的方法";
    }

    @LogAnnotation
    @RequestMapping("/student")
    public StudentResp getStudent() {
        Student stu = cac.getBean(Student.class);
        stu.setAge(stu.getAge());
        return new StudentResp(stu.getAge(), stu.getName());
    }
}
