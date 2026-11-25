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
 * 检查上传的文件，再交给 {@link ObjectStorage} 保存。
 * 收款和报修仍只收 jpg、png、webp，默认不超过 10MB。
 * 协议材料（biz=agreement）还可以收录音和文档，大小分别用配置里的 audio-max-bytes、doc-max-bytes。
 */
@Service
public class FileUploadService {

    private static final int MAX_COUNT = 9;
    private static final Set<String> FOLDERS = Set.of("payment", "repair", "common", "agreement");

    private final ObjectStorage storage;
    private final long maxBytes;
    private final long audioMaxBytes;
    private final long docMaxBytes;

    public FileUploadService(ObjectStorage storage, ZhongbangProperties properties) {
        this.storage = storage;
        this.maxBytes = properties.getStorage().getMaxBytes();
        this.audioMaxBytes = properties.getStorage().getAudioMaxBytes();
        this.docMaxBytes = properties.getStorage().getDocMaxBytes();
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
            saved.add("agreement".equals(folder) ? storeAgreement(file, folder) : storeOne(file, folder));
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
        byte[] bytes = read(file, "读取图片失败");
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
        return saveImage(folder, file.getOriginalFilename(), sniffed, bytes);
    }

    private UploadedFileVo storeAgreement(MultipartFile file, String folder) {
        byte[] bytes = read(file, "读取文件失败");
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择文件");
        }
        String extension = extension(file.getOriginalFilename());
        String kind = kindOf(extension);
        if (kind == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "协议材料只支持图片（jpg/png/webp）、录音（mp3/m4a/wav/amr）和文档（pdf/doc/docx/xls/xlsx）");
        }
        long limit = switch (kind) {
            case "AUDIO" -> audioMaxBytes;
            case "DOCUMENT" -> docMaxBytes;
            default -> maxBytes;
        };
        if (bytes.length > limit) {
            String label = "AUDIO".equals(kind) ? "录音" : "DOCUMENT".equals(kind) ? "文档" : "图片";
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + mb(limit));
        }
        if ("IMAGE".equals(kind)) {
            String sniffed = sniff(bytes);
            if (sniffed == null || (extension != null && !sameKind(extension, sniffed))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 jpg、png、webp");
            }
            return saveImage(folder, file.getOriginalFilename(), sniffed, bytes);
        }
        if (!magicMatches(kind, extension, bytes)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容和扩展名对不上");
        }
        String contentType = contentTypeOf(extension);
        StoredObject stored = storage.store(folder, extension, contentType, bytes);
        String name = file.getOriginalFilename() == null ? stored.objectKey() : file.getOriginalFilename();
        return new UploadedFileVo(stored.url(), name, stored.size(), stored.contentType(), kind);
    }

    private UploadedFileVo saveImage(String folder, String originalName, String sniffed, byte[] bytes) {
        String storedType = switch (sniffed) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
        String storedExt = "png".equals(sniffed) ? "png" : "webp".equals(sniffed) ? "webp" : "jpg";
        StoredObject stored = storage.store(folder, storedExt, storedType, bytes);
        String name = originalName == null ? stored.objectKey() : originalName;
        return new UploadedFileVo(stored.url(), name, stored.size(), stored.contentType(), "IMAGE");
    }

    private static byte[] read(MultipartFile file, String message) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /** 业务目录只允许固定的几个名字，避免路径里出现 ../。没传就放 common。 */
    private static String folder(String biz) {
        if (biz == null || biz.isBlank()) {
            return "common";
        }
        String text = biz.trim().toLowerCase(Locale.ROOT);
        if (!FOLDERS.contains(text)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务类型只能是 payment、repair、common 或 agreement");
        }
        return text;
    }

    private static String kindOf(String extension) {
        if (extension == null) {
            return null;
        }
        return switch (extension) {
            case "jpg", "jpeg", "png", "webp" -> "IMAGE";
            case "mp3", "m4a", "wav", "amr" -> "AUDIO";
            case "pdf", "doc", "docx", "xls", "xlsx" -> "DOCUMENT";
            default -> null;
        };
    }

    private static String contentTypeOf(String extension) {
        return switch (extension) {
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "wav" -> "audio/wav";
            case "amr" -> "audio/amr";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    /** 用文件头确认不是改了后缀的文本。docx/xlsx 都是 zip，所以还要看扩展名。 */
    static boolean magicMatches(String kind, String extension, byte[] bytes) {
        if ("AUDIO".equals(kind)) {
            return switch (extension) {
                case "mp3" -> isMp3(bytes);
                case "wav" -> isRiff(bytes, "WAVE");
                case "amr" -> startsWith(bytes, "#!AMR");
                case "m4a" -> bytes.length > 8 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
                default -> false;
            };
        }
        return switch (extension) {
            case "pdf" -> startsWith(bytes, "%PDF");
            case "doc", "xls" -> isOle(bytes);
            case "docx", "xlsx" -> isZip(bytes);
            default -> false;
        };
    }

    private static boolean isMp3(byte[] bytes) {
        if (startsWith(bytes, "ID3")) {
            return true;
        }
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0;
    }

    private static boolean isRiff(byte[] bytes, String kind) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == kind.charAt(0) && bytes[9] == kind.charAt(1) && bytes[10] == kind.charAt(2) && bytes[11] == kind.charAt(3);
    }

    private static boolean isOle(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0xD0
                && (bytes[1] & 0xFF) == 0xCF
                && (bytes[2] & 0xFF) == 0x11
                && (bytes[3] & 0xFF) == 0xE0;
    }

    private static boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private static boolean startsWith(byte[] bytes, String text) {
        if (bytes.length < text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (bytes[i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String mb(long bytes) {
        long value = Math.max(1, (bytes + 1024 * 1024 - 1) / (1024 * 1024));
        return value + "MB";
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
