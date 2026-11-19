package com.chaosthedude.explorerscompass.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.util.Identifier;

record StructureSearchQuery(String source, String dimension, String set, String structure, String phrase, String[] fragments) {
    private static final Pattern TOKEN = Pattern.compile("\\S+");

    static List<StructureSearchQuery> parseAlternatives(String query, boolean anyOrder) {
        List<StructureSearchQuery> alternatives = new ArrayList<>();
        boolean hasQuery = false;
        for (String segment : query.split(";")) {
            if (segment.isBlank()) continue;
            hasQuery = true;
            StructureSearchQuery alternative = parse(segment, anyOrder);
            if (alternative != null) alternatives.add(alternative);
        }
        return hasQuery ? alternatives : List.of(parse("", anyOrder));
    }

    static StructureSearchQuery parse(String query, boolean anyOrder) {
        query = query.strip().toLowerCase(Locale.ROOT);
        var tokens = TOKEN.matcher(query);
        String source = null, dimension = null, set = null, structure = null;
        int firstPlain = -1;
        int lastPlainEnd = -1;
        boolean reachedTrailingPrefix = false;
        StringBuilder plain = new StringBuilder();
        while (tokens.find()) {
            String token = tokens.group();
            char prefix = token.charAt(0);
            if (prefix == '@' || prefix == '$' || prefix == '%' || prefix == '&') {
                if (firstPlain >= 0) reachedTrailingPrefix = true;
                String value = token.substring(1);
                switch (prefix) {
                    case '@' -> {
                        if (source != null) return null;
                        source = value;
                    }
                    case '%' -> {
                        if (dimension != null) return null;
                        dimension = value;
                    }
                    case '&' -> {
                        if (set != null) return null;
                        set = value;
                    }
                    case '$' -> {
                        if (structure != null) return null;
                        structure = value;
                    }
                }
            } else {
                if (!anyOrder && reachedTrailingPrefix) return null;
                if (firstPlain < 0) firstPlain = tokens.start();
                lastPlainEnd = tokens.end();
                if (anyOrder) plain.append(token).append(' ');
            }
        }
        String phrase = firstPlain < 0 ? "" : query.substring(firstPlain, lastPlainEnd);
        return new StructureSearchQuery(source, dimension, set, structure, phrase,
                anyOrder ? SettingsSearch.fragments(plain.toString()) : null);
    }

    static boolean matchesFilter(String query, String name, String id) {
        query = query.strip().toLowerCase(Locale.ROOT);
        if (query.startsWith("$")) {
            return id != null && id.toLowerCase(Locale.ROOT).contains(query.substring(1));
        }
        return name.toLowerCase(Locale.ROOT).contains(query);
    }

    boolean matches(Identifier id, String name, Collection<Identifier> dimensions, Identifier setId) {
        if (source != null && !id.getNamespace().contains(source)) return false;
        if (dimension != null && dimensions.stream().noneMatch(value -> value.toString().contains(dimension))) return false;
        if (set != null && (setId == null || !setId.toString().contains(set))) return false;
        String readable = name.toLowerCase(Locale.ROOT);
        if (structure != null && !id.toString().contains(structure)) return false;
        return fragments == null
                ? readable.contains(phrase)
                : SettingsSearch.matches(readable, fragments, null);
    }
}
