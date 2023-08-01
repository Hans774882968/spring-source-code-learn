package com.hans.aop_demo.anno_anno;

public class Student {
    private Integer age;
    private String name;

    @LogAnnotation
    public void setAge(Integer age) {
        this.age = age;
    }

    @LogAnnotation
    public Integer getAge() {
        return age;
    }

    @LogAnnotation
    public void setName(String name) {
        this.name = name;
    }

    @LogAnnotation
    public String getName() {
        return name;
    }

    public void printThrowException() {
        System.out.println("Exception raised");
        throw new IllegalArgumentException();
    }
}
