package com.zhongbang.controller.adapter.oss;

/**
 * 保存图片的接口。业务代码只依赖它，不关心文件在本机还是阿里云。
 *
 * <p>现在容器里只有 {@link LocalFileStorage}。以后要换成 OSS，新写一个实现类，
 * 用 {@code zhongbang.storage.type=oss} 把它装进容器，上传接口不用改。
 */
public interface ObjectStorage {

    /**
     * 写入一张已经校验过的图片。
     *
     * @param folder      业务目录，例如 payment、repair，只含字母和数字
     * @param extension   不带点的扩展名，例如 jpg
     * @param contentType 返回给浏览器的类型，例如 image/jpeg
     * @param bytes       文件内容
     * @return 浏览器能直接打开的地址，以及内部用的对象键
     */
    StoredObject store(String folder, String extension, String contentType, byte[] bytes);
}
