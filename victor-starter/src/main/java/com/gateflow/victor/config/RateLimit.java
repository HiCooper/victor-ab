package com.gateflow.victor.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解 — 基于 Redis 的简单令牌桶限流。
 * 标记在 Controller 方法上，限制每秒请求数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 每秒最大请求数 */
    int value() default 10;
}
