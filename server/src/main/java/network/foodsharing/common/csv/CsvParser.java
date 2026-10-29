package network.foodsharing.common.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * Small RFC 4180 CSV reader for admin imports (regions, past EinAbs): strips a UTF-8 BOM, detects the delimiter
 * ({@code ,} {@code ;} or tab, whichever occurs most in the first line outside quotes), supports quoted fields with
 * doubled quotes and line breaks, ignores completely empty lines.
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<List<String>> parse(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String input = text.charAt(0) == '﻿' ? text.substring(1) : text;
        char delimiter = detectDelimiter(input);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < input.length() && input.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            addRow(rows, row);
        }
        return rows;
    }

    /** True for a comment line: its first cell starts with {@code #} (admin imports document their data this way). */
    public static boolean isComment(List<String> row) {
        return !row.isEmpty() && row.get(0).strip().startsWith("#");
    }

    static char detectDelimiter(String text) {
        int comma = 0;
        int semicolon = 0;
        int tab = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '\n' || c == '\r')) {
                break;
            } else if (!quoted && c == ',') {
                comma++;
            } else if (!quoted && c == ';') {
                semicolon++;
            } else if (!quoted && c == '\t') {
                tab++;
            }
        }
        if (tab > comma && tab > semicolon) {
            return '\t';
        }
        return semicolon > comma ? ';' : ',';
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(cell -> !cell.isBlank())) {
            rows.add(row);
        }
    }
}
