package com.sky.annotation;

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/***
 * 自定义注解用来标识哪些方法需要进行功能字段自动填充处理,比如 createTime、updateTime、createUser、updateUser
 */
@Target(ElementType.METHOD)//这个注解只能加在方法上
@Retention(RetentionPolicy.RUNTIME)//这个注解在程序运行的时候仍然存在，可以被反射或 AOP 读取到。
public @interface AutoFill {
    //数据库操作类型:UPDATE INSERT
    OperationType value();
}
