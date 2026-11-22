package io.github.enriquemetner.explorerscompass.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

record ServerToml(CommentedConfig config, List<String> lines) {
    static ServerToml read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("File is missing or is not a regular file");
        String source = Files.readString(path);
        var config = TomlFormat.newConfig(LinkedHashMap::new);
        try (var reader = new LineNumberReader(new StringReader(source))) {
            try {
                new TomlParser().parse(reader, config, ParsingMode.MERGE);
            } catch (RuntimeException error) {
                int line = Math.min(reader.getLineNumber() + 1, Math.max(1, (int) source.lines().count()));
                throw new IllegalArgumentException("Invalid TOML near line " + line + ": " + StartupConfigReport.cause(error), error);
            }
        }
        return new ServerToml(config, source.lines().toList());
    }

    String assignmentLocation(String key) {
        String leaf = key.substring(key.lastIndexOf('.') + 1);
        return uniqueLocation(Pattern.compile("^\\s*(?:" + Pattern.quote(key) + "|" + Pattern.quote(leaf) + ")\\s*="));
    }

    String selectorLocation(String selector) {
        return uniqueLocation(Pattern.compile("[\"']" + Pattern.quote(selector) + "[\"']"));
    }

    static String quotedDiagnosticValue(Object value) {
        return new com.google.gson.JsonPrimitive(String.valueOf(value)).toString();
    }

    private String uniqueLocation(Pattern pattern) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (!line.stripLeading().startsWith("#") && pattern.matcher(line).find()) {
                if (found != -1) return "";
                found = i + 1;
            }
        }
        return found == -1 ? "" : " at line " + found;
    }
}
