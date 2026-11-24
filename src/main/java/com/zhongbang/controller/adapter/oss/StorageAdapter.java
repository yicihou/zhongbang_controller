package com.zhongbang.controller.adapter.oss;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 上传凭证占位。没有任何接口调用它。
 *
 * <p>{@code issueUploadPolicy} 不连接 OSS / MinIO。{@code bizType} 只被拼进返回的目录名，
 * 例如传入 {@code REPAIR} 得到 {@code repair/}。返回值里的 {@code note=stub-policy} 用来标明这是假数据。
 */
@Component
public class StorageAdapter {

    public Map<String, String> issueUploadPolicy(String bizType) {
        return Map.of(
                "bizType", bizType,
                "uploadHost", "https://example-oss.local",
                "dir", bizType.toLowerCase() + "/",
                "note", "stub-policy"
        );
    }
}
