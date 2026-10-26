/*
 * DexPatcher - Copyright 2015-2026 Pfuenzle
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package lanchon.dexpatcher.core.finder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses the DexExplore advanced query syntax used by {@code @DexFinder}.
 *
 * <p>Methods: {@code m:public+final, p:a.B+c.D, r:x.Y, n:methodName, z:0}
 * <p>Fields:  {@code m:public+final, t:a.B, n:fieldName}
 * <p>Classes: {@code m:public+final, s:a.Super, i:a.Iface, a:a.Annot}
 *
 * <p>Each token is {@code key:value1+value2+...}; whitespace around commas is
 * ignored. Unknown keys are rejected so that typos surface immediately rather
 * than being silently ignored.
 */
public final class FinderQuery {

    public enum Kind { METHOD, FIELD, CLASS, ANY }

    // Shared modifiers (access flags keyword list)
    public final List<String> modifiers;

    // Method-specific
    public final List<String> paramTypes;
    public final String returnType;

    // Field-specific
    public final String fieldType;

    // Shared member/class name filter
    public final String name;

    // Class-specific
    public final String superClass;
    public final List<String> interfaces;
    public final List<String> annotations;

    // Code-size filter (method)
    public final int minSize;    // -1 = unset

    // Semantic filters, resolved against the class analysis.
    // 'l:' matches a method by a string literal it contains (for example
    //     the "isPro=" label of a generated Kotlin data class toString()).
    // 'f:' matches a method by the field it reads, or a field by its label.
    // 'g:' matches a getter, that is, a method that reads exactly the field
    //     given by 'f:' and returns it.
    public final String literal;
    public final String field;
    public final boolean getter;

    public final Kind kind;

    private FinderQuery(
            List<String> modifiers,
            List<String> paramTypes,
            String returnType,
            String fieldType,
            String name,
            String superClass,
            List<String> interfaces,
            List<String> annotations,
            int minSize,
            String literal,
            String field,
            boolean getter,
            Kind kind) {
        this.modifiers  = modifiers;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
        this.fieldType  = fieldType;
        this.name       = name;
        this.superClass = superClass;
        this.interfaces = interfaces;
        this.annotations = annotations;
        this.minSize    = minSize;
        this.literal    = literal;
        this.field      = field;
        this.getter     = getter;
        this.kind       = kind;
    }

    /**
     * Parse a finder query string.
     *
     * @throws FinderQueryException if the query is syntactically invalid
     */
    public static FinderQuery parse(String query) throws FinderQueryException {
        if (query == null || query.trim().isEmpty()) {
            throw new FinderQueryException("finder query must not be empty");
        }

        List<String> modifiers  = null;
        List<String> paramTypes = null;
        String returnType  = null;
        String fieldType   = null;
        String name        = null;
        String superClass  = null;
        List<String> interfaces  = null;
        List<String> annotations = null;
        int minSize = -1;
        String literal = null;
        String field = null;
        boolean getter = false;

        String[] tokens = query.split(",");
        for (String raw : tokens) {
            String token = raw.trim();
            if (token.isEmpty()) continue;

            int colon = token.indexOf(':');
            if (colon < 0) {
                throw new FinderQueryException("missing ':' in finder query token: '" + token + "'");
            }
            String key   = token.substring(0, colon).trim();
            String value = token.substring(colon + 1).trim();
            if (value.isEmpty()) {
                throw new FinderQueryException("empty value for key '" + key + "' in finder query");
            }

            switch (key) {
                case "m":
                    if (modifiers != null) throw dupKey(key);
                    modifiers = splitPlus(value);
                    break;
                case "p":
                    if (paramTypes != null) throw dupKey(key);
                    paramTypes = splitPlus(value);
                    break;
                case "r":
                    if (returnType != null) throw dupKey(key);
                    returnType = value;
                    break;
                case "t":
                    if (fieldType != null) throw dupKey(key);
                    fieldType = value;
                    break;
                case "n":
                    if (name != null) throw dupKey(key);
                    name = value;
                    break;
                case "s":
                    if (superClass != null) throw dupKey(key);
                    superClass = value;
                    break;
                case "i":
                    if (interfaces != null) throw dupKey(key);
                    interfaces = splitPlus(value);
                    break;
                case "a":
                    if (annotations != null) throw dupKey(key);
                    annotations = splitPlus(value);
                    break;
                case "l": {
                    if (literal != null) throw dupKey(key);
                    literal = value;
                    break;
                }
                case "f": {
                    if (field != null) throw dupKey(key);
                    field = value;
                    break;
                }
                case "g": {
                    if (getter) throw dupKey(key);
                    getter = true;
                    break;
                }
                case "z":
                    if (minSize != -1) throw dupKey(key);
                    try {
                        minSize = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new FinderQueryException("invalid integer for key 'z': '" + value + "'");
                    }
                    break;
                default:
                    throw new FinderQueryException("unknown key '" + key + "' in finder query");
            }
        }

        // Infer kind: field-specific keys imply FIELD, method-specific imply METHOD,
        // class-specific imply CLASS, otherwise ANY.
        boolean hasField  = (fieldType != null);
        boolean hasMethod = (paramTypes != null || returnType != null || minSize >= 0);
        boolean hasClass  = (superClass != null || interfaces != null);

        if (hasField && hasMethod) {
            throw new FinderQueryException("finder query mixes field keys (t:) with method keys (p:,r:,z:)");
        }
        if (hasField && hasClass) {
            throw new FinderQueryException("finder query mixes field keys (t:) with class keys (s:,i:)");
        }
        if (hasMethod && hasClass) {
            throw new FinderQueryException("finder query mixes method keys (p:,r:,z:) with class keys (s:,i:)");
        }

        Kind kind;
        if (hasField)       kind = Kind.FIELD;
        else if (hasMethod) kind = Kind.METHOD;
        else if (hasClass)  kind = Kind.CLASS;
        else                kind = Kind.ANY;

        return new FinderQuery(
                unmodifiable(modifiers),
                unmodifiable(paramTypes),
                returnType,
                fieldType,
                name,
                superClass,
                unmodifiable(interfaces),
                unmodifiable(annotations),
                minSize,
                literal,
                field,
                getter,
                kind);
    }

    // ---- helpers ----

    private static FinderQueryException dupKey(String key) {
        return new FinderQueryException("duplicate key '" + key + "' in finder query");
    }

    private static List<String> splitPlus(String value) {
        String[] parts = value.split("\\+");
        List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private static <T> List<T> unmodifiable(List<T> list) {
        return list == null ? Collections.<T>emptyList() : Collections.unmodifiableList(list);
    }

}
