package com.chaosthedude.explorerscompass.workers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Append completed stages without keeping an open file between searches or game launches. */
final class BenchmarkResults {
    static final String HEADER = "timestamp,variant,id,search,worker,outcome,origin_x,origin_z,result_x,result_z,distance,elapsed_ms,candidates,checks,chunk_requests,check_ms,placement_rejections";

    static synchronized void append(Path file, Object... fields) throws IOException {
        if (fields.length != 17) throw new IllegalArgumentException("Expected 17 benchmark fields");
        appendWithHeader(file, HEADER, fields);
    }

    static synchronized void appendWithHeader(Path file, String header, Object... fields) throws IOException {
        Files.createDirectories(file.getParent());
        boolean needsHeader = !Files.exists(file) || Files.size(file) == 0;
        String row = Arrays.stream(fields).map(BenchmarkResults::escape).collect(Collectors.joining(","));
        Files.writeString(file, (needsHeader ? header + "\n" : "") + row + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String escape(Object value) {
        String text = value == null ? "" : value.toString();
        return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }
}
