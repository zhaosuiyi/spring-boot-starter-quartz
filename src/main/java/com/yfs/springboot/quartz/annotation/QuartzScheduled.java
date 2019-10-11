package com.yfs.springboot.quartz.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 注解在方法上的Quartz Cron注解 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QuartzScheduled {
    String cron() default "";
    String desc() default "";
    String taskName() default "";
    String taskGroup() default "DEFAULT";
    String email() default "";
}
