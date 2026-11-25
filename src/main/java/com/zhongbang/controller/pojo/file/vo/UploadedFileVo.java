package com.zhongbang.controller.pojo.file.vo;

/**
 * 上传成功后返回给页面的一张图。{@code url} 是相对地址，例如 {@code /uploads/payment/20261125/abc.jpg}。
 */
public record UploadedFileVo(
        String url,
        String name,
        long size,
        String contentType
) {
}
