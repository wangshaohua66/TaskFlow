package com.taskflow.exception;

/**
 * 业务规则违反异常
 */
public class BusinessRuleException extends TodoException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
