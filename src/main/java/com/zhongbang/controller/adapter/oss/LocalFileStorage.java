package com.zhongbang.controller.adapter.oss;

import com.zhongbang.controller.config.ZhongbangProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 把图片写到本机目录。默认目录是进程工作目录下的 {@code ./uploads}，不打进 jar。
 *
 * <p>文件名用随机串，避免猜到别人的转账截图。访问地址是 {@code /uploads/业务/日期/文件名}，
 * 由 {@code WebConfig} 映射到这个目录，所以页面用普通 img 就能打开，不必再带登录令牌。
 *
 * <p>{@code zhongbang.storage.type} 不是 local（或没写）时，这个类不会装进容器。
 */
@Component
@ConditionalOnProperty(prefix = "zhongbang.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements ObjectStorage {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final ZhongbangProperties.Storage storage;

    public LocalFileStorage(ZhongbangProperties properties) {
        this.storage = properties.getStorage();
    }

    @Override
    public StoredObject store(String folder, String extension, String contentType, byte[] bytes) {
        String day = LocalDate.now().format(DAY);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = storage.directory().resolve(folder).resolve(day);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("保存图片失败", ex);
        }
        String key = folder + "/" + day + "/" + name;
        return new StoredObject(storage.publicPath() + "/" + key, key, bytes.length, contentType);
    }
}
