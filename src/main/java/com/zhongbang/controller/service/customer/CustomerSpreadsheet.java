package com.zhongbang.controller.service.customer;

import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.pojo.customer.dto.CustomerRequests;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户名单的 CSV 和 xlsx。第一行是中文表头，导入时按表头认列，多出来的列会忽略。
 * 这是简单版本：一次最多 500 行，手机号重复的行记入错误，不中断其他行。
 */
@Component
public class CustomerSpreadsheet {

    static final String[] HEADERS = {
            "姓名", "手机", "备用电话", "微信号", "客户类型", "地区", "厂名或店名", "机器数量",
            "主营产品", "来源", "介绍人", "生日", "备注", "标签", "等级"
    };

    public byte[] template(String format) {
        return "xlsx".equalsIgnoreCase(format) ? xlsx(List.of()) : csv(List.of());
    }

    public byte[] export(List<CustomerVo.ListItem> rows, String format) {
        List<String[]> data = new ArrayList<>();
        for (CustomerVo.ListItem row : rows) {
            data.add(new String[]{
                    text(row.name()),
                    text(row.mobile()),
                    "",
                    "",
                    text(row.customerTypeLabel()),
                    text(row.region()),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    text(row.tags()),
                    text(row.levelName())
            });
        }
        return "xlsx".equalsIgnoreCase(format) ? xlsx(data) : csv(data);
    }

    public List<ImportRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要导入的文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            byte[] bytes = file.getBytes();
            if (name.endsWith(".xlsx") || (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K')) {
                return parseXlsx(bytes);
            }
            return parseCsv(new String(stripBom(bytes), StandardCharsets.UTF_8));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取文件，请使用下载的模板");
        }
    }

    private List<ImportRow> parseCsv(String text) {
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件是空的");
        }
        int[] index = headerIndex(splitCsv(lines[0]));
        List<ImportRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (rows.size() >= 500) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多导入 500 行");
            }
            rows.add(toRow(index, splitCsv(lines[i])));
        }
        return rows;
    }

    private List<ImportRow> parseXlsx(byte[] bytes) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = book.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件是空的");
            }
            int[] index = headerIndex(cells(sheet.getRow(0)));
            List<ImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String[] values = cells(row);
                if (String.join("", values).isBlank()) {
                    continue;
                }
                if (rows.size() >= 500) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多导入 500 行");
                }
                rows.add(toRow(index, values));
            }
            return rows;
        }
    }

    private static ImportRow toRow(int[] index, String[] cells) {
        return new ImportRow(toProfile(index, cells), at(cells, index.length > 14 ? index[14] : -1));
    }

    private static CustomerRequests.Profile toProfile(int[] index, String[] cells) {
        return new CustomerRequests.Profile(
                at(cells, index[0]),
                at(cells, index[1]),
                at(cells, index[2]),
                at(cells, index[3]),
                at(cells, index[4]),
                at(cells, index[6]),
                number(at(cells, index[7])),
                at(cells, index[8]),
                at(cells, index[9]),
                at(cells, index[10]),
                at(cells, index[11]),
                at(cells, index[12]),
                at(cells, index[13]),
                at(cells, index[5])
        );
    }

    /** 返回每个模板列在文件中的下标，找不到的是 -1。姓名和手机必须有。 */
    private static int[] headerIndex(String[] header) {
        int[] index = new int[HEADERS.length];
        for (int i = 0; i < HEADERS.length; i++) {
            index[i] = -1;
            for (int c = 0; c < header.length; c++) {
                if (HEADERS[i].equals(header[c] == null ? "" : header[c].trim())) {
                    index[i] = c;
                    break;
                }
            }
        }
        if (index[0] < 0 || index[1] < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "表头需要包含「姓名」和「手机」");
        }
        return index;
    }

    private byte[] csv(List<String[]> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append(String.join(",", HEADERS)).append('\n');
        for (String[] row : rows) {
            for (int i = 0; i < HEADERS.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(escape(i < row.length ? row[i] : ""));
            }
            builder.append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(List<String[]> rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("客户");
            writeRow(sheet.createRow(0), HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                writeRow(sheet.createRow(i + 1), pad(rows.get(i)));
            }
            book.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出 Excel 失败");
        }
    }

    private static void writeRow(Row row, String[] values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i] == null ? "" : values[i]);
        }
    }

    private static String[] pad(String[] row) {
        String[] values = new String[HEADERS.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = i < row.length && row[i] != null ? row[i] : "";
        }
        return values;
    }

    private static String[] cells(Row row) {
        if (row == null) {
            return new String[0];
        }
        int last = Math.max(row.getLastCellNum(), 0);
        String[] values = new String[last];
        for (int i = 0; i < last; i++) {
            values[i] = row.getCell(i) == null ? "" : row.getCell(i).toString().trim();
        }
        return values;
    }

    private static String[] splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quote = !quote;
                }
            } else if (c == ',' && !quote) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells.toArray(String[]::new);
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String at(String[] cells, int index) {
        if (index < 0 || index >= cells.length) {
            return null;
        }
        String text = cells[index];
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Integer number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            byte[] copy = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, copy, 0, copy.length);
            return copy;
        }
        return bytes;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** 导入的一行。levelName 是表里的等级名称或编码，空表示不手动指定。 */
    public record ImportRow(CustomerRequests.Profile profile, String levelName) {
    }
}
