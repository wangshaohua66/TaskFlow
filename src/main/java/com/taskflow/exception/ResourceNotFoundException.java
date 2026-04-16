package com.taskflow.exception;

/**
 * 资源未找到异常
 */
public class ResourceNotFoundException extends TodoException {
    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " 不存在, ID: " + id);
    }
}
