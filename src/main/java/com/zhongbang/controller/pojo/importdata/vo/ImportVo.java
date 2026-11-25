package com.zhongbang.controller.pojo.importdata.vo;

import java.util.List;
import java.util.Map;

/** 导入预览。先看列对应关系和每一行的错误，确认后再写入。出错的行跳过，其他行继续。 */
public final class ImportVo {

    private ImportVo() {
    }

    public record Field(String key, String label, boolean required) {
    }

    public record PreviewRow(int rowNo, Map<String, String> cells, String error) {
    }

    public record Preview(
            String token,
            String kind,
            List<Field> fields,
            List<String> headers,
            Map<String, String> suggestedMapping,
            List<PreviewRow> rows
    ) {
    }

    public record CommitResult(int created, int skipped, List<String> errors) {
    }

    public record DemoResult(boolean created, int suppliers, int purchases, int orders, String message) {
    }
}
