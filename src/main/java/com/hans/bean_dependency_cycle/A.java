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
