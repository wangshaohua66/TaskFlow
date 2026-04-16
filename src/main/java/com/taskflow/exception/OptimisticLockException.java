package com.taskflow.exception;

/**
 * 乐观锁冲突异常 - 用于测试并发场景
 */
public class OptimisticLockException extends TodoException {
    public OptimisticLockException(Long id) {
        super("任务 " + id + " 已被其他用户修改,请刷新后重试");
    }
}
