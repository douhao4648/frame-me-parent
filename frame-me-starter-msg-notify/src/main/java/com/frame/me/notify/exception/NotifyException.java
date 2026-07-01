package com.frame.me.notify.exception;

/**
 * 通知模块异常.
 */
public class NotifyException extends RuntimeException {

    public NotifyException(String message) {
        super(message);
    }

    public NotifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
