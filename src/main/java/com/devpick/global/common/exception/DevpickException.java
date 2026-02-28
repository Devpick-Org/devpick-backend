package com.devpick.global.common.exception;

import lombok.Getter;

@Getter
public class DevpickException extends RuntimeException {

    private final ErrorCode errorCode;

    public DevpickException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public DevpickException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
