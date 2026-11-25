package com.zhongbang.controller.service.file;

import com.zhongbang.controller.adapter.oss.ObjectStorage;
import com.zhongbang.controller.adapter.oss.StoredObject;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.config.ZhongbangProperties;
import com.zhongbang.controller.pojo.file.vo.UploadedFileVo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 检查上传的图片，再交给 {@link ObjectStorage} 保存。
 * 类型只允许 jpg、png、webp，大小默认不超过 10MB。既看扩展名和 Content-Type，也看文件头，
 * 避免把改了后缀的文本当成图片。
 */
@Service
public class FileUploadService {

    private static final int MAX_COUNT = 9;
    private static final Set<String> FOLDERS = Set.of("payment", "repair", "common");

    private final ObjectStorage storage;
    private final long maxBytes;

    public FileUploadService(ObjectStorage storage, ZhongbangProperties properties) {
        this.storage = storage;
        this.maxBytes = properties.getStorage().getMaxBytes();
    }

    public List<UploadedFileVo> storeAll(List<MultipartFile> files, String biz) {
        if (files == null || files.isEmpty() || files.stream().allMatch(file -> file == null || file.isEmpty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择图片");
        }
        if (files.size() > MAX_COUNT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多上传 " + MAX_COUNT + " 张");
        }
        String folder = folder(biz);
        List<UploadedFileVo> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            saved.add(storeOne(file, folder));
        }
        if (saved.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择图片");
        }
        return saved;
    }

    private UploadedFileVo storeOne(MultipartFile file, String folder) {
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能超过 10MB");
        }
        byte[] bytes = read(file);
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择图片");
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能超过 10MB");
        }
        String sniffed = sniff(bytes);
        if (sniffed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 jpg、png、webp");
        }
        String extension = extension(file.getOriginalFilename());
        if (extension != null && !sameKind(extension, sniffed)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 jpg、png、webp");
        }
        String contentType = normalizeType(file.getContentType());
        if (contentType != null && !sameKind(contentType, sniffed)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 jpg、png、webp");
        }
        String storedType = switch (sniffed) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
        String storedExt = "png".equals(sniffed) ? "png" : "webp".equals(sniffed) ? "webp" : "jpg";
        StoredObject stored = storage.store(folder, storedExt, storedType, bytes);
        String name = file.getOriginalFilename() == null ? stored.objectKey() : file.getOriginalFilename();
        return new UploadedFileVo(stored.url(), name, stored.size(), stored.contentType());
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取图片失败");
        }
    }

    /** 业务目录只允许固定的几个名字，避免路径里出现 ../。没传就放 common。 */
    private static String folder(String biz) {
        if (biz == null || biz.isBlank()) {
            return "common";
        }
        String text = biz.trim().toLowerCase(Locale.ROOT);
        if (!FOLDERS.contains(text)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务类型只能是 payment、repair 或 common");
        }
        return text;
    }

    private static String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String text = contentType.toLowerCase(Locale.ROOT);
        int semi = text.indexOf(';');
        if (semi >= 0) {
            text = text.substring(0, semi).trim();
        }
        if (text.isBlank() || "application/octet-stream".equals(text)) {
            return null;
        }
        return text;
    }

    /** jpg 和 jpeg 算同一种。 */
    private static boolean sameKind(String hint, String sniffed) {
        String kind = switch (hint) {
            case "jpg", "jpeg", "image/jpeg", "image/jpg", "image/pjpeg" -> "jpg";
            case "png", "image/png" -> "png";
            case "webp", "image/webp" -> "webp";
            default -> hint;
        };
        return sniffed.equals(kind);
    }

    /**
     * 看文件开头的几个字节判断真实类型。
     * JPEG 以 FF D8 FF 开头，PNG 以固定的 8 字节开头，WEBP 是 RIFF 容器里带着 WEBP。
     */
    static String sniff(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return "png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
