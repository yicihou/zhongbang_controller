/**
 * 图片存储。
 *
 * <p>{@code ObjectStorage} 是业务要用的接口，{@code LocalFileStorage} 把文件写到本机目录。
 * 以后换成阿里云 OSS 时，新写一个实现即可，上传接口不用改。
 * {@code StorageAdapter} 仍是早先的直传凭证占位，没有接口调用它。
 */
package com.zhongbang.controller.adapter.oss;
