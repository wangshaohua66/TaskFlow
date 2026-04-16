package com.taskflow.exception;

/**
 * 自定义异常基类
 */
public class TodoException extends RuntimeException {
    public TodoException(String message) {
        super(message);
    }

    public TodoException(String message, Throwable cause) {
        super(message, cause);
    }
}
