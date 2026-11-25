package com.zhongbang.controller.controller.customer;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.pojo.customer.dto.CustomerRequests;
import com.zhongbang.controller.pojo.customer.entity.CustomerLevelRule;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.service.customer.CustomerDemoService;
import com.zhongbang.controller.service.customer.CustomerInsightService;
import com.zhongbang.controller.service.customer.CustomerLevelService;
import com.zhongbang.controller.service.customer.CustomerProfileService;
import com.zhongbang.controller.service.customer.CustomerQueryService;
import com.zhongbang.controller.service.customer.CustomerSpreadsheet;
import com.zhongbang.controller.service.customer.RepayAgreementService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户模块。类上的 {@code @PreAuthorize} 要求角色是老板。
 * 管理员、业务员、师傅即使已经登录后台，调用这里也会得到 403，不是只在菜单里隐藏。
 */
@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('BOSS')")
public class AdminCustomerController {

    private final CustomerQueryService queryService;
    private final CustomerProfileService profileService;
    private final CustomerLevelService levelService;
    private final RepayAgreementService agreementService;
    private final CustomerInsightService insightService;
    private final CustomerDemoService demoService;
    private final CustomerSpreadsheet spreadsheet;

    public AdminCustomerController(
            CustomerQueryService queryService,
            CustomerProfileService profileService,
            CustomerLevelService levelService,
            RepayAgreementService agreementService,
            CustomerInsightService insightService,
            CustomerDemoService demoService,
            CustomerSpreadsheet spreadsheet
    ) {
        this.queryService = queryService;
        this.profileService = profileService;
        this.levelService = levelService;
        this.agreementService = agreementService;
        this.insightService = insightService;
        this.demoService = demoService;
        this.spreadsheet = spreadsheet;
    }

    @GetMapping
    public ApiResponse<PageResult<CustomerVo.ListItem>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String unpaidOnly,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.list(name, mobile, region, tag, customerType, level, truthy(unpaidOnly), sort, direction, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String unpaidOnly,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format) || "xls".equalsIgnoreCase(format);
        byte[] body = spreadsheet.export(queryService.exportRows(name, mobile, region, tag, customerType, level, truthy(unpaidOnly), sort, direction), xlsx ? "xlsx" : "csv");
        return file(body, xlsx ? "客户.xlsx" : "客户.csv", xlsx);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template(@RequestParam(defaultValue = "csv") String format) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        return file(spreadsheet.template(xlsx ? "xlsx" : "csv"), xlsx ? "客户导入模板.xlsx" : "客户导入模板.csv", xlsx);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CustomerVo.ImportResult> importFile(@RequestParam("file") MultipartFile file) {
        List<CustomerSpreadsheet.ImportRow> rows = spreadsheet.parse(file);
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            CustomerSpreadsheet.ImportRow row = rows.get(i);
            try {
                CustomerVo.Detail detail = profileService.create(row.profile());
                String levelName = row.levelName();
                if (levelName != null && !levelName.isBlank()) {
                    String code = levelCode(levelName);
                    profileService.assignLevel(detail.profile().id(), new CustomerRequests.LevelAssign(code, true));
                }
                created++;
            } catch (BusinessException ex) {
                skipped++;
                if (errors.size() < 20) {
                    errors.add("第 " + (i + 2) + " 行：" + ex.getMessage());
                }
            }
        }
        return ApiResponse.ok(new CustomerVo.ImportResult(created, skipped, errors));
    }

    @PostMapping
    public ApiResponse<CustomerVo.Detail> create(@RequestBody CustomerRequests.Profile request) {
        return ApiResponse.ok(profileService.create(request));
    }

    @PostMapping("/demo")
    public ApiResponse<CustomerVo.DemoResult> demo() {
        return ApiResponse.ok(demoService.seed());
    }

    @DeleteMapping("/demo")
    public ApiResponse<CustomerVo.DemoResult> clearDemo() {
        return ApiResponse.ok(demoService.clear());
    }

    @GetMapping("/overview")
    public ApiResponse<CustomerVo.Overview> overview() {
        return ApiResponse.ok(queryService.overview());
    }

    @PostMapping("/insights")
    public ApiResponse<CustomerVo.Insight> overallInsight() {
        return ApiResponse.ok(insightService.analyzeOverall());
    }

    @GetMapping("/insights")
    public ApiResponse<List<CustomerVo.Analysis>> overallHistory() {
        return ApiResponse.ok(insightService.history(null, "OVERALL"));
    }

    @GetMapping("/levels")
    public ApiResponse<List<CustomerVo.LevelRule>> levels() {
        return ApiResponse.ok(levelService.list());
    }

    @PutMapping("/levels/{id}")
    public ApiResponse<CustomerVo.LevelRule> updateLevel(@PathVariable Long id, @RequestBody CustomerRequests.LevelRule request) {
        return ApiResponse.ok(levelService.update(id, request));
    }

    @GetMapping("/repayments")
    public ApiResponse<PageResult<CustomerVo.Agreement>> repayments(
            @RequestParam(defaultValue = "OVERDUE") String bucket,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(agreementService.reminders(bucket, page, size));
    }

    @PostMapping("/repayments/scan")
    public ApiResponse<Integer> scan() {
        return ApiResponse.ok(agreementService.scan(null));
    }

    @PutMapping("/agreements/{id}")
    public ApiResponse<CustomerVo.Agreement> updateAgreement(@PathVariable Long id, @RequestBody CustomerRequests.Agreement request) {
        return ApiResponse.ok(agreementService.update(id, request));
    }

    @PostMapping("/agreements/{id}/fulfill")
    public ApiResponse<CustomerVo.Agreement> fulfill(@PathVariable Long id) {
        return ApiResponse.ok(agreementService.fulfill(id));
    }

    @PostMapping("/agreements/{id}/void")
    public ApiResponse<CustomerVo.Agreement> voidAgreement(@PathVariable Long id) {
        return ApiResponse.ok(agreementService.voidAgreement(id));
    }

    @GetMapping("/agreements/{id}/history")
    public ApiResponse<List<CustomerVo.AgreementLog>> history(@PathVariable Long id) {
        return ApiResponse.ok(agreementService.history(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerVo.Detail> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerVo.Detail> update(@PathVariable Long id, @RequestBody CustomerRequests.Profile request) {
        return ApiResponse.ok(profileService.update(id, request));
    }

    @PostMapping("/{id}/addresses")
    public ApiResponse<CustomerVo.Detail> addAddress(@PathVariable Long id, @RequestBody CustomerRequests.Address request) {
        return ApiResponse.ok(profileService.addAddress(id, request));
    }

    @PutMapping("/{id}/addresses/{addressId}")
    public ApiResponse<CustomerVo.Detail> updateAddress(
            @PathVariable Long id,
            @PathVariable Long addressId,
            @RequestBody CustomerRequests.Address request
    ) {
        return ApiResponse.ok(profileService.updateAddress(id, addressId, request));
    }

    @PostMapping("/{id}/addresses/{addressId}/default")
    public ApiResponse<CustomerVo.Detail> defaultAddress(@PathVariable Long id, @PathVariable Long addressId) {
        return ApiResponse.ok(profileService.defaultAddress(id, addressId));
    }

    @PostMapping("/{id}/follows")
    public ApiResponse<CustomerVo.Detail> follow(@PathVariable Long id, @RequestBody CustomerRequests.Follow request) {
        return ApiResponse.ok(profileService.addFollow(id, request));
    }

    @PutMapping("/{id}/preference")
    public ApiResponse<CustomerVo.Detail> preference(@PathVariable Long id, @RequestBody CustomerRequests.Preference request) {
        return ApiResponse.ok(profileService.savePreference(id, request));
    }

    @PutMapping("/{id}/level")
    public ApiResponse<CustomerVo.Detail> level(@PathVariable Long id, @RequestBody CustomerRequests.LevelAssign request) {
        return ApiResponse.ok(profileService.assignLevel(id, request));
    }

    @PostMapping("/{id}/agreements")
    public ApiResponse<CustomerVo.Detail> agreement(@PathVariable Long id, @RequestBody CustomerRequests.Agreement request) {
        return ApiResponse.ok(agreementService.create(id, request));
    }

    @PostMapping("/{id}/insights")
    public ApiResponse<CustomerVo.Insight> insight(@PathVariable Long id) {
        return ApiResponse.ok(insightService.analyzeCustomer(id));
    }

    @GetMapping("/{id}/insights")
    public ApiResponse<List<CustomerVo.Analysis>> insightHistory(@PathVariable Long id) {
        return ApiResponse.ok(insightService.history(id, "CUSTOMER"));
    }

    private String levelCode(String name) {
        String text = name.trim();
        for (CustomerLevelRule rule : levelService.rules()) {
            if (rule.getCode().equalsIgnoreCase(text) || rule.getName().equals(text)) {
                return rule.getCode();
            }
        }
        throw new BusinessException(com.zhongbang.controller.common.exception.ErrorCode.BAD_REQUEST, "等级「" + text + "」不存在");
    }

    private static boolean truthy(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static ResponseEntity<byte[]> file(byte[] body, String filename, boolean xlsx) {
        MediaType type = xlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv;charset=UTF-8");
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(type)
                .body(body);
    }
}
