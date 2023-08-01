package com.hans.aop_demo.anno_anno;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogAdvice {
    @Pointcut("@annotation(com.hans.aop_demo.anno_anno.LogAnnotation)")
    public void logPc() {
    }

    @Around("logPc()")
    public Object aroundAdvice(ProceedingJoinPoint proceedingJoinPoint) {
        Long startTime = System.currentTimeMillis();
        Object result = null;
        try {
            if (proceedingJoinPoint.getSignature().getName().equals("setAge") && proceedingJoinPoint.getSignature()
                    .getDeclaringTypeName().equals("com.hans.aop_demo.anno_anno.Student")) {
                Object[] args = proceedingJoinPoint.getArgs();
                if (args.length == 1 && args[0] instanceof Integer) {
                    int originalAge = (Integer) (args[0]);
                    args[0] = Integer.valueOf(originalAge + 1);
                    result = proceedingJoinPoint.proceed(args);
                }
            } else {
                result = proceedingJoinPoint.proceed();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        Long endTime = System.currentTimeMillis();
        System.out.printf("%s:: Execution time: %d ms\n", proceedingJoinPoint.getSignature(), endTime - startTime);
        return result;
    }
}
