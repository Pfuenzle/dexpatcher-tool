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
import java.util.List;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;

/**
 * Matches dexlib2 members against a {@link FinderQuery}.
 *
 * <ul>
 * <li>{@code m:} lists access flag keywords; every listed flag must be set.</li>
 * <li>{@code p:} lists parameter types; all must match exactly.</li>
 * <li>{@code r:} / {@code t:} match the return / field type.</li>
 * <li>{@code n:} matches the member name exactly.</li>
 * <li>{@code s:} / {@code i:} / {@code a:} match the class hierarchy.</li>
 * </ul>
 */
public final class FinderMatcher {

    private final FinderQuery query;
    private ClassAnalysis analysis;

    public FinderMatcher(FinderQuery query) {
        this.query = query;
    }

    /**
     * Set the analysis of the class the members are searched in. It is used to
     * resolve the semantic filters ({@code l:}, {@code f:} and {@code g:}).
     */
    public void setAnalysis(ClassAnalysis analysis) {
        this.analysis = analysis;
    }

    public ClassAnalysis getAnalysis() {
        return analysis;
    }

    public FinderQuery getQuery() {
        return query;
    }

    // Methods

    public boolean matches(Method method) {
        if (!matchesModifiers(method.getAccessFlags())) return false;
        if (query.name != null && !query.name.equals(method.getName())) return false;
        if (!matchesLiteral(method)) return false;
        if (!matchesField(method)) return false;
        if (query.getter && !isGetterOf(method)) return false;
        if (!matchesParameters(method)) return false;
        if (query.returnType != null &&
                !matchesType(query.returnType, method.getReturnType())) return false;
        if (query.minSize >= 0) {
            int size = instructionCount(method);
            if (size < query.minSize) return false;
        }
        return true;
    }

    private boolean matchesParameters(Method method) {
        if (query.paramTypes.isEmpty()) return true;
        List<? extends CharSequence> actual = method.getParameterTypes();
        if (actual.size() != query.paramTypes.size()) return false;
        for (int i = 0; i < actual.size(); i++) {
            if (!matchesType(query.paramTypes.get(i), actual.get(i))) return false;
        }
        return true;
    }

    private static int instructionCount(Method method) {
        if (method.getImplementation() == null) return 0;
        int size = 0;
        for (Instruction ignored : method.getImplementation().getInstructions()) size++;
        return size;
    }

    // Fields

    public boolean matches(Field field) {
        if (!matchesModifiers(field.getAccessFlags())) return false;
        if (query.name != null && !query.name.equals(field.getName())) return false;
        if (!matchesFieldLabel(field)) return false;
        if (query.fieldType != null && !matchesType(query.fieldType, field.getType())) return false;
        return true;
    }

    // Classes

    public boolean matches(ClassDef classDef) {
        if (!matchesModifiers(classDef.getAccessFlags())) return false;
        if (query.name != null && !query.name.equals(simpleName(classDef.getType()))) return false;
        if (query.superClass != null) {
            String superClass = classDef.getSuperclass();
            if (superClass == null || !matchesType(query.superClass, superClass)) return false;
        }
        if (!query.interfaces.isEmpty()) {
            for (String iface : query.interfaces) {
                if (!containsType(classDef.getInterfaces(), iface)) return false;
            }
        }
        if (!query.annotations.isEmpty()) {
            for (String annotation : query.annotations) {
                if (!containsAnnotation(classDef.getAnnotations(), annotation)) return false;
            }
        }
        return true;
    }

    private static boolean containsType(Iterable<? extends CharSequence> types, String type) {
        for (CharSequence actual : types) {
            if (matchesType(type, actual)) return true;
        }
        return false;
    }

    private static boolean containsAnnotation(Iterable<? extends Annotation> annotations, String type) {
        for (Annotation annotation : annotations) {
            if (matchesType(type, annotation.getType())) return true;
        }
        return false;
    }

    // Semantic filters

    private boolean matchesLiteral(Method method) {
        if (query.literal == null) return true;
        if (analysis == null) return false;
        for (String literal : analysis.getLiterals(method)) {
            if (literal.contains(query.literal)) return true;
        }
        return false;
    }

    private boolean matchesField(Method method) {
        if (query.field == null) return true;
        if (analysis == null) return false;
        List<String> required = resolveFields();
        if (required.isEmpty()) return false;
        List<String> read = analysis.getReadFields(method);
        return read.containsAll(required);
    }

    private boolean matchesFieldLabel(Field field) {
        if (query.literal == null) return true;
        if (analysis == null) return false;
        String resolved = analysis.getFieldForLiteral(query.literal);
        return resolved != null && resolved.equals(field.getName());
    }

    /**
     * A getter is a method that reads exactly the given fields and returns one
     * of them, taking no arguments. This is how a Kotlin property accessor
     * looks after obfuscation.
     */
    private boolean isGetterOf(Method method) {
        if (query.field == null) return false;
        if (!method.getParameterTypes().isEmpty()) return false;
        if (analysis == null) return false;
        List<String> required = resolveFields();
        if (required.isEmpty()) return false;
        List<String> read = analysis.getReadFields(method);
        return read.size() == required.size() && read.containsAll(required);
    }

    /**
     * Resolve the field specifications of the query into field names.
     *
     * <p>A specification is either a field name, or a label such as
     * {@code isPro=} that identifies the field through the class analysis.
     * Multiple specifications are separated by a plus sign.
     */
    private List<String> resolveFields() {
        List<String> result = new ArrayList<>();
        if (query.field == null) return result;
        for (String spec : query.field.split("\\+")) {
            spec = spec.trim();
            if (spec.isEmpty()) continue;
            String resolved = spec;
            if (spec.contains("=") && analysis != null) {
                String field = analysis.getFieldForLiteral(spec);
                if (field == null) return new ArrayList<String>();
                resolved = field;
            }
            if (!result.contains(resolved)) result.add(resolved);
        }
        return result;
    }

    // Shared

    private boolean matchesModifiers(int accessFlags) {
        for (String modifier : query.modifiers) {
            AccessFlags flag = AccessFlags.getAccessFlag(modifier);
            if (flag == null) {
                throw new IllegalArgumentException("unknown access flag keyword: '" + modifier + "'");
            }
            if ((accessFlags & flag.getValue()) == 0) return false;
        }
        return true;
    }

    /**
     * Compare a type name from a finder query with a dex type descriptor.
     * The query may use a fully qualified class name ({@code com.example.Foo}),
     * a descriptor ({@code Lcom/example/Foo;}) or a primitive name ({@code boolean}).
     */
    static boolean matchesType(String queryType, CharSequence descriptor) {
        String actual = descriptor.toString();
        if (queryType.equals(actual)) return true;
        if (actual.startsWith("L") && actual.endsWith(";")) {
            String className = actual.substring(1, actual.length() - 1).replace('/', '.');
            if (queryType.equals(className)) return true;
        }
        if (actual.startsWith("[")) {
            // Array types are compared by their element type.
            return matchesType(queryType, actual.substring(1));
        }
        String primitive = primitiveName(actual);
        return primitive != null && primitive.equals(queryType);
    }

    private static String primitiveName(String descriptor) {
        switch (descriptor) {
            case "Z": return "boolean";
            case "C": return "char";
            case "B": return "byte";
            case "S": return "short";
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            case "V": return "void";
            default: return null;
        }
    }

    private static String simpleName(String descriptor) {
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) return descriptor;
        String name = descriptor.substring(1, descriptor.length() - 1);
        int index = name.lastIndexOf('/');
        return index < 0 ? name : name.substring(index + 1);
    }

}
