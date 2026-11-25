package com.zhongbang.controller.pojo.file.vo;

/**
 * 上传成功后返回给页面的一个文件。{@code url} 是相对地址，例如 {@code /uploads/payment/20261125/abc.jpg}。
 * {@code kind} 是 IMAGE、AUDIO 或 DOCUMENT，页面据此决定播放还是下载。
 */
public record UploadedFileVo(
        String url,
        String name,
        long size,
        String contentType,
        String kind
) {
}
