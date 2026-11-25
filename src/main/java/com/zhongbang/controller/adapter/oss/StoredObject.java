package com.zhongbang.controller.adapter.oss;

/**
 * 一次保存的结果。{@code url} 给页面放进 img 的 src；{@code objectKey} 是存储里的相对路径，换 OSS 时也可以当对象名。
 */
public record StoredObject(
        String url,
        String objectKey,
        long size,
        String contentType
) {
}
