package com.gateflow.victor.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解 — 基于 Redis 的滑动窗口限流。
 * 标记在 Controller 方法上，限制指定时间窗口内的最大请求数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 时间窗口内最大请求数 */
    int value() default 10;

    /** 时间窗口大小（秒），默认 1 秒 */
    int windowSeconds() default 1;
}
