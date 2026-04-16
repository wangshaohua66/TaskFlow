package com.taskflow.model;

/**
 * 优先级枚举 - 支持自动升级逻辑
 */
public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /**
     * 升级到下一级优先级
     * CRITICAL不能再升级
     */
    public Priority upgrade() {
        switch (this) {
            case LOW: return MEDIUM;
            case MEDIUM: return HIGH;
            case HIGH: return CRITICAL;
            case CRITICAL: return CRITICAL; // 已经是最高级
            default: return this;
        }
    }
}
