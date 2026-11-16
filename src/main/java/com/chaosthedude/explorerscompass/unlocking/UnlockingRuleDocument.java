package com.chaosthedude.explorerscompass.unlocking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class UnlockingRuleDocument {
    private static final Pattern LINE = Pattern.compile("line (\\d+)");
    // Gson's strict reader still accepts raw controls in strings and case-insensitive literals.
    private static final Pattern LEXEME = Pattern.compile(
            "[ \\t\\r\\n]+|[{}\\[\\],:]|true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"
            + "|\"(?:[^\"\\\\\\x00-\\x1f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*+\"");
    private final Map<JsonElement, Integer> lines = new IdentityHashMap<>();
    private final Map<JsonObject, Map<String, Integer>> fieldLines = new IdentityHashMap<>();
    final JsonElement root;

    UnlockingRuleDocument(String source) {
        validateLexemes(source);
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            root = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Unexpected content after the JSON document at line " + line(reader));
        } catch (IOException | IllegalStateException e) {
            String message = e.getMessage();
            String lenientHint = "Use JsonReader.setLenient(true) to accept malformed JSON";
            if (message != null && message.startsWith(lenientHint))
                message = "Malformed JSON" + message.substring(lenientHint.length());
            throw new IllegalArgumentException(message, e);
        }
    }

    private static void validateLexemes(String source) {
        var tokens = LEXEME.matcher(source);
        int end = 0;
        while (end < source.length()) {
            tokens.region(end, source.length());
            if (!tokens.lookingAt()) {
                long line = source.substring(0, end).chars().filter(c -> c == '\n').count() + 1;
                throw new IllegalArgumentException("Invalid JSON token at line " + line);
            }
            end = tokens.end();
        }
    }

    int fieldLine(JsonObject object, String key) {
        return fieldLines.get(object).get(key);
    }

    int line(JsonElement value) {
        return lines.getOrDefault(value, 1);
    }

    private static int line(JsonReader reader) {
        var match = LINE.matcher(reader.toString());
        return match.find() ? Integer.parseInt(match.group(1)) : 1;
    }

    private JsonElement read(JsonReader reader, int depth) throws IOException {
        JsonToken token = reader.peek();
        int line = line(reader);
        if (depth > 128) throw new IllegalArgumentException("JSON nesting exceeds 128 levels at line " + line);
        JsonElement value = switch (token) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                Map<String, Integer> positions = new HashMap<>();
                fieldLines.put(object, positions);
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    positions.put(key, line(reader));
                    object.add(key, read(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new LazilyParsedNumber(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield new JsonNull();
            }
            default -> throw new IllegalArgumentException("Expected a JSON value at line " + line);
        };
        lines.put(value, line);
        return value;
    }
}
