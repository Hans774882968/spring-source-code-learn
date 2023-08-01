package com.hans.aop_demo.anno;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecTimeAdvice {
    @Pointcut("execution(* com.hans.aop_demo.anno.*.*(..))")
    public void foo() {
    }

    @Around("foo()")
    public Object aroundAdvice(ProceedingJoinPoint proceedingJoinPoint) {
        Long startTime = System.currentTimeMillis();
        Object result = null;
        try {
            result = proceedingJoinPoint.proceed();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        Long endTime = System.currentTimeMillis();
        System.out.printf("%s:: Execution time: %d ms\n", proceedingJoinPoint.getSignature(), endTime - startTime);
        return result;
    }
}
