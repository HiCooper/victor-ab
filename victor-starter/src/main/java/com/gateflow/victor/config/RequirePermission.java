package com.gateflow.victor.config;

import com.gateflow.victor.domain.entity.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要权限的注解
 * 标记在 Controller 方法上，指定需要的权限
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 需要的权限
     */
    Permission value();
}