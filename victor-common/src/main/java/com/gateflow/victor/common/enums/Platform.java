package com.gateflow.victor.common.enums;

import lombok.Getter;

/**
 * 平台枚举
 */
@Getter
public enum Platform {

    WEB("web", "Web端"),
    ANDROID("android", "Android端"),
    IOS("ios", "iOS端"),
    SERVER("server", "服务端");

    private final String code;
    private final String description;

    Platform(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Platform fromCode(String code) {
        for (Platform platform : values()) {
            if (platform.getCode().equals(code)) {
                return platform;
            }
        }
        return null;
    }
}