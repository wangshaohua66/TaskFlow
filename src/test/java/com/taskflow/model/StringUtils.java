package com.taskflow.model;

/**
 * 字符串工具类 (Java 8 兼容)
 */
public class StringUtils {
    /**
     * 重复字符串指定次数
     *
     * @param str   要重复的字符串
     * @param count 重复次数
     * @return 重复后的字符串
     */
    public static String repeat(String str, int count) {
        if (str == null || count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
