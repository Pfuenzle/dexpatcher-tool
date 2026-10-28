/*
 * DexWeaver - Copyright 2026 Pfuenzle
 * (GNU General Public License version 3 or later)
 *
 * DexWeaver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DexWeaver is a fork of DexPatcher (https://github.com/DexPatcher/dexpatcher-tool)
 * by Rodrigo Balerdi (Lanchon), which is licensed under the same terms.
 */

package io.pfuenzle.dexweaver.core.finder;

import java.util.ArrayList;
import java.util.List;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;

/**
 * Searches a set of source classes for the members matching a finder query.
 */
public final class FinderSearch {

    private final FinderMatcher matcher;

    public FinderSearch(FinderQuery query) {
        this.matcher = new FinderMatcher(query);
    }

    public FinderMatcher getMatcher() {
        return matcher;
    }

    /**
     * Find the methods matching the query.
     *
     * @param sourceClasses the source classes to search in
     * @param searchClass   the descriptor of the class to search in, or null to
     *                      search in all given classes
     * @return the matching methods, in source order
     */
    public List<Method> findMethods(Iterable<? extends ClassDef> sourceClasses, String searchClass) {
        List<Method> result = new ArrayList<>();
        for (ClassDef classDef : sourceClasses) {
            if (searchClass != null && !searchClass.equals(classDef.getType())) continue;
            if (matcher.requiresClassAnalysis()) matcher.setAnalysis(new ClassAnalysis(classDef));
            for (Method method : classDef.getMethods()) {
                if (matcher.matches(method)) result.add(method);
            }
        }
        return result;
    }

    /**
     * Find the fields matching the query.
     */
    public List<Field> findFields(Iterable<? extends ClassDef> sourceClasses, String searchClass) {
        List<Field> result = new ArrayList<>();
        for (ClassDef classDef : sourceClasses) {
            if (searchClass != null && !searchClass.equals(classDef.getType())) continue;
            if (matcher.requiresClassAnalysis()) matcher.setAnalysis(new ClassAnalysis(classDef));
            for (Field field : classDef.getFields()) {
                if (matcher.matches(field)) result.add(field);
            }
        }
        return result;
    }

    /**
     * Find the classes matching the query.
     */
    public List<ClassDef> findClasses(Iterable<? extends ClassDef> sourceClasses) {
        List<ClassDef> result = new ArrayList<>();
        for (ClassDef classDef : sourceClasses) {
            if (matcher.matches(classDef)) result.add(classDef);
        }
        return result;
    }

}
