package com.hans.bean_dependency_cycle.hans;

public class B {
    private A a;

    public A getA() {
        return this.a;
    }

    public void setA(A a) {
        this.a = a;
    }

}
