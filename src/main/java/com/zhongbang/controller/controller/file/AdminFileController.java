package com.zhongbang.controller.controller.file;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.file.vo.UploadedFileVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.service.file.FileUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 员工上传图片。路径在 {@code /api/admin/**} 下，要带员工登录令牌。
 *
 * <p>表单字段名是 {@code file}，可以重复多次，一次最多 9 张。
 * {@code biz} 选填：payment 收款凭证、repair 报修照片、不传则放 common。
 * 小程序报修目前仍把图片地址写在 JSON 里，没有走这个接口；师傅或员工如果以后要传现场照，可以复用它。
 */
@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {

    private final FileUploadService fileUploadService;

    public AdminFileController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping
    public ApiResponse<List<UploadedFileVo>> upload(
            @RequestParam("file") List<MultipartFile> files,
            @RequestParam(required = false) String biz
    ) {
        if (biz != null && "agreement".equalsIgnoreCase(biz.trim())) {
            StaffRoles.requireBoss("只有老板可以上传协议材料");
        }
        return ApiResponse.ok(fileUploadService.storeAll(files, biz));
    }
}
