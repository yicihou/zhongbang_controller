package com.zhongbang.controller.controller.importdata;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.importdata.vo.ImportVo;
import com.zhongbang.controller.service.importdata.TradeImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 商品进价、供应商、采购和历史销售的导入。先预览再确认。
 * 金额在表格里按元填写。只有老板能用，因为里面有进价和销售额。
 */
@RestController
@RequestMapping("/api/admin/imports")
@PreAuthorize("hasRole('BOSS')")
public class AdminImportController {

    private final TradeImportService tradeImportService;

    public AdminImportController(TradeImportService tradeImportService) {
        this.tradeImportService = tradeImportService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(
            @RequestParam(defaultValue = "PRODUCT") String kind,
            @RequestParam(defaultValue = "csv") String format
    ) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] body = tradeImportService.template(kind, xlsx ? "xlsx" : "csv");
        String filename = "导入模板-" + kind + (xlsx ? ".xlsx" : ".csv");
        MediaType type = xlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv;charset=UTF-8");
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).contentType(type).body(body);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportVo.Preview> preview(@RequestParam(defaultValue = "PRODUCT") String kind, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(tradeImportService.preview(kind, file));
    }

    @PostMapping("/commit")
    public ApiResponse<ImportVo.CommitResult> commit(@RequestBody Map<String, Object> body) {
        String token = body.get("token") == null ? "" : String.valueOf(body.get("token"));
        @SuppressWarnings("unchecked")
        Map<String, String> mapping = body.get("mapping") instanceof Map<?, ?> map
                ? map.entrySet().stream().collect(java.util.stream.Collectors.toMap(entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue())))
                : Map.of();
        return ApiResponse.ok(tradeImportService.commit(token, mapping));
    }
}
