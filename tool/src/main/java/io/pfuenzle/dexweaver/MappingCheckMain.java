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

package io.pfuenzle.dexweaver;

import io.pfuenzle.dexweaver.core.Context;
import io.pfuenzle.dexweaver.core.PatcherAnnotation;
import io.pfuenzle.dexweaver.core.finder.ClassAnalysis;
import io.pfuenzle.dexweaver.core.finder.FinderQuery;
import io.pfuenzle.dexweaver.core.finder.FinderQueryException;
import io.pfuenzle.dexweaver.core.finder.FinderResolver;
import io.pfuenzle.dexweaver.core.finder.FinderSpec;
import lanchon.multidexlib2.BasicDexFileNamer;
import lanchon.multidexlib2.DexFileNamer;
import lanchon.multidexlib2.MultiDexIO;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Member;
import com.android.tools.smali.dexlib2.iface.Method;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Standalone validation of all @DexFinder mappings in a compiled patch dex. */
public final class MappingCheckMain {

    private static final String DEFAULT_ANNOTATION_PACKAGE = Context.DEFAULT_ANNOTATION_PACKAGE;

    public static void main(String[] args) throws Exception {
        System.exit(run(args));
    }

    public static int run(String[] args) throws Exception {
        String annotationPackage = DEFAULT_ANNOTATION_PACKAGE;
        boolean multiDex = false;
        List<String> files = new ArrayList<>();
        boolean check = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--check-mappings".equals(arg)) {
                check = true;
            } else if ("--multi-dex".equals(arg)) {
                multiDex = true;
            } else if ("--annotations".equals(arg) && i + 1 < args.length) {
                annotationPackage = args[++i];
            } else if (!arg.startsWith("--")) {
                files.add(arg);
            } else {
                throw new IllegalArgumentException("unknown mapping-check option: " + arg);
            }
        }
        if (!check || files.size() < 2) {
            System.err.println("usage: MappingCheckMain --check-mappings [--multi-dex] " +
                    "[--annotations <package>] <source-dex-apk-or-dir> <patch-dex-or-dir> ...");
            return 2;
        }

        DexFileNamer namer = new BasicDexFileNamer();
        Opcodes opcodes = null;
        DexFile source = MultiDexIO.readDexFile(multiDex, new File(files.get(0)), namer, opcodes, null);
        List<ClassDef> sourceClasses = new ArrayList<>(source.getClasses());
        Context context = new Context.Builder().setAnnotationPackage(annotationPackage).build();
        boolean success = true;
        int mappings = 0;

        for (int i = 1; i < files.size(); i++) {
            DexFile patch = MultiDexIO.readDexFile(multiDex, new File(files.get(i)), namer, source.getOpcodes(), null);
            for (ClassDef patchClass : patch.getClasses()) {
                PatcherAnnotation classAnnotation = parse(context, patchClass.getAnnotations());
                ClassDef targetClass = findExact(sourceClasses, patchClass.getType());
                if (classAnnotation != null && classAnnotation.getFinder() != null) {
                    mappings++;
                    FinderSpec spec = classAnnotation.getFinder();
                    ClassDef found = resolveClass(sourceClasses, patchClass.getType(), spec);
                    if (found == null) {
                        success = false;
                        System.out.println("MISSING class " + patchClass.getType() + " <- " + spec);
                    } else {
                        targetClass = found;
                        System.out.println("FOUND   class " + patchClass.getType() + " -> " +
                                found.getType() + " <- " + spec);
                    }
                }

                if (targetClass == null) {
                    for (Method method : patchClass.getMethods()) {
                        if (hasFinder(context, method)) {
                            mappings++;
                            success = false;
                            System.out.println("MISSING method " + patchClass.getType() + "." +
                                    method.getName() + " (target class not found)");
                        }
                    }
                    for (Field field : patchClass.getFields()) {
                        if (hasFinder(context, field)) {
                            mappings++;
                            success = false;
                            System.out.println("MISSING field " + patchClass.getType() + "." +
                                    field.getName() + " (target class not found)");
                        }
                    }
                    continue;
                }

                ClassAnalysis analysis = new ClassAnalysis(targetClass);
                for (Method method : patchClass.getMethods()) {
                    PatcherAnnotation annotation = parse(context, method.getAnnotations());
                    if (annotation == null || annotation.getFinder() == null) continue;
                    mappings++;
                    Member found = resolveMember(sourceClasses, targetClass, analysis, annotation.getFinder());
                    if (found == null) {
                        success = false;
                        System.out.println("MISSING method " + patchClass.getType() + "." +
                                method.getName() + " <- " + annotation.getFinder());
                    } else {
                        System.out.println("FOUND   method " + patchClass.getType() + "." +
                                method.getName() + " -> " + found.getDefiningClass() + "." +
                                found.getName() + " <- " + annotation.getFinder());
                    }
                }
                for (Field field : patchClass.getFields()) {
                    PatcherAnnotation annotation = parse(context, field.getAnnotations());
                    if (annotation == null || annotation.getFinder() == null) continue;
                    mappings++;
                    Member found = resolveMember(sourceClasses, targetClass, analysis, annotation.getFinder());
                    if (found == null) {
                        success = false;
                        System.out.println("MISSING field " + patchClass.getType() + "." +
                                field.getName() + " <- " + annotation.getFinder());
                    } else {
                        System.out.println("FOUND   field " + patchClass.getType() + "." +
                                field.getName() + " -> " + found.getDefiningClass() + "." +
                                found.getName() + " <- " + annotation.getFinder());
                    }
                }
            }
        }

        if (mappings == 0) {
            System.out.println("No @DexFinder mappings found.");
            return 0;
        }
        System.out.println((success ? "Mapping check passed: " : "Mapping check failed: ") +
                mappings + " mapping(s).");
        return success ? 0 : 1;
    }

    private static ClassDef resolveClass(Collection<ClassDef> sourceClasses, String patchType,
            FinderSpec spec) throws FinderQueryException {
        FinderResolver resolver = new FinderResolver(spec);
        FinderQuery.Kind kind = resolver.getQuery().kind;
        String searchClass = (kind == FinderQuery.Kind.METHOD || kind == FinderQuery.Kind.FIELD)
                ? null : FinderResolver.resolveSearchClass(spec, patchType);
        return resolver.resolveClassTarget(sourceClasses, searchClass);
    }

    private static Member resolveMember(Collection<ClassDef> sourceClasses, ClassDef targetClass,
            ClassAnalysis analysis, FinderSpec spec) throws FinderQueryException {
        FinderResolver resolver = new FinderResolver(spec);
        resolver.getMatcher().setAnalysis(analysis);
        String searchClass = FinderResolver.resolveSearchClass(spec, targetClass.getType());
        return resolver.resolveMember(sourceClasses, searchClass);
    }

    private static boolean hasFinder(Context context, com.android.tools.smali.dexlib2.iface.Annotatable item)
            throws Exception {
        PatcherAnnotation annotation = parse(context, item.getAnnotations());
        return annotation != null && annotation.getFinder() != null;
    }

    private static PatcherAnnotation parse(Context context,
            java.util.Set<? extends com.android.tools.smali.dexlib2.iface.Annotation> annotations) throws Exception {
        return PatcherAnnotation.parse(context.getActionParser(), annotations);
    }

    private static ClassDef findExact(Collection<ClassDef> classes, String type) {
        for (ClassDef classDef : classes) {
            if (type.equals(classDef.getType())) return classDef;
        }
        return null;
    }

    private MappingCheckMain() {}
}
