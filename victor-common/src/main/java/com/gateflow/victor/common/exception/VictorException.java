package com.gateflow.victor.common.exception;

import com.gateflow.victor.common.constant.ErrorCode;

/**
 * Victor 基础异常类
 */
public class VictorException extends RuntimeException {

    private final String errorCode;
    private final String message;

    public VictorException(String message) {
        super(message);
        this.errorCode = "VICTOR_ERROR";
        this.message = message;
    }

    public VictorException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public VictorException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode.getCode();
        this.message = errorCode.getMessage() + ": " + detail;
    }

    public VictorException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }

    public VictorException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }
}