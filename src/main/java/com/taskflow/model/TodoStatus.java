package com.taskflow.model;

/**
 * Todo状态枚举 - 状态机约束
 * 合法转换:
 * PENDING -> IN_PROGRESS, CANCELLED
 * IN_PROGRESS -> COMPLETED, PENDING, CANCELLED
 * COMPLETED -> (无,终态)
 * CANCELLED -> PENDING (可重新激活)
 * EXPIRED -> IN_PROGRESS, CANCELLED
 */
public enum TodoStatus {
    PENDING,        // 待处理
    IN_PROGRESS,    // 进行中
    COMPLETED,      // 已完成(终态)
    CANCELLED,      // 已取消
    EXPIRED         // 已过期
}
