package com.chaosthedude.explorerscompass.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.util.Identifier;

record StructureSearchQuery(String source, String dimension, String set, String phrase, String[] fragments) {
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
        String source = null, dimension = null, set = null;
        int firstPlain = -1;
        StringBuilder plain = new StringBuilder();
        while (tokens.find()) {
            String token = tokens.group();
            char prefix = token.charAt(0);
            if (prefix == '@' || prefix == '$' || prefix == '&') {
                if (!anyOrder && firstPlain >= 0) return null;
                String value = token.substring(1);
                switch (prefix) {
                    case '@' -> {
                        if (source != null) return null;
                        source = value;
                    }
                    case '$' -> {
                        if (dimension != null) return null;
                        dimension = value;
                    }
                    case '&' -> {
                        if (set != null) return null;
                        set = value;
                    }
                }
            } else {
                if (firstPlain < 0) firstPlain = tokens.start();
                if (anyOrder) plain.append(token).append(' ');
            }
        }
        boolean prefixed = source != null || dimension != null || set != null;
        String phrase = !prefixed ? query : firstPlain < 0 ? "" : query.substring(firstPlain);
        return new StructureSearchQuery(source, dimension, set, phrase,
                anyOrder ? SettingsSearch.fragments(plain.toString()) : null);
    }

    boolean matches(Identifier id, String name, Collection<Identifier> dimensions, Identifier setId, boolean allowIds) {
        if (source != null && !id.getNamespace().contains(source)) return false;
        if (dimension != null && dimensions.stream().noneMatch(value -> value.toString().contains(dimension))) return false;
        if (set != null && (setId == null || !setId.toString().contains(set))) return false;
        String readable = name.toLowerCase(Locale.ROOT);
        String technical = allowIds ? id.toString() : null;
        return fragments == null
                ? readable.contains(phrase) || (technical != null && technical.contains(phrase))
                : SettingsSearch.matches(readable, fragments, technical);
    }
}
