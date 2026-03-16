package com.devpick.global.common.exception;

import lombok.Getter;

@Getter
public class DevpickException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object detail;

    public DevpickException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public DevpickException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public DevpickException(ErrorCode errorCode, Object detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
