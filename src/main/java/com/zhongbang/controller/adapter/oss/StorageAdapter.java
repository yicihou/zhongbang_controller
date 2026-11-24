package com.zhongbang.controller.adapter.oss;

import org.springframework.stereotype.Component;

import java.util.Map;

/** OSS/MinIO signing stub (docs §4.8). */
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
