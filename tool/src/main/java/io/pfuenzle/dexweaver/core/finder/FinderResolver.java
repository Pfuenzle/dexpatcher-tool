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

import io.pfuenzle.dexweaver.core.util.TypeName;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Member;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;

import java.util.List;

/**
 * Resolves the patch target described by a finder specification against the
 * source classes.
 */
public final class FinderResolver {

	private final FinderSearch search;
	private final FinderQuery query;

	public FinderResolver(FinderSpec spec) throws FinderQueryException {
		this.query = FinderQuery.parse(spec.getQuery());
		this.search = new FinderSearch(query);
	}

	public FinderQuery getQuery() {
		return query;
	}

	public FinderMatcher getMatcher() {
		return search.getMatcher();
	}

	/**
	 * Resolve the class to patch.
	 *
	 * @param sourceClasses the source classes
	 * @param searchClass   the descriptor of the class to search in, or null to
	 *                      search in all source classes
	 * @return the matching class, or null if there is no match
	 */
	public ClassDef resolveClass(Iterable<? extends ClassDef> sourceClasses, String searchClass) {
		List<ClassDef> classes = search.findClasses(sourceClasses);
		if (classes.isEmpty()) return null;
		if (searchClass != null) {
			for (ClassDef classDef : classes) {
				if (searchClass.equals(classDef.getType())) return classDef;
			}
			return null;
		}
		return classes.get(0);
	}

	/**
	 * Resolve the method to patch.
	 *
	 * @param sourceClasses the source classes
	 * @param searchClass   the descriptor of the class to search in, or null to
	 *                      search in all source classes
	 * @return the matching method, or null if there is no match
	 */
	public Method resolveMethod(Iterable<? extends ClassDef> sourceClasses, String searchClass) {
		List<Method> methods = search.findMethods(sourceClasses, searchClass);
		return methods.isEmpty() ? null : methods.get(0);
	}

	/**
	 * Resolve the field to patch.
	 */
	public Field resolveField(Iterable<? extends ClassDef> sourceClasses, String searchClass) {
		List<Field> fields = search.findFields(sourceClasses, searchClass);
		return fields.isEmpty() ? null : fields.get(0);
	}

	/**
	 * Resolve the member to patch.
	 *
	 * <p>The kind of member is determined by the query: a query with method
	 * keys resolves to a method, a query with field keys resolves to a field.
	 *
	 * @param sourceClasses the source classes
	 * @param searchClass   the descriptor of the class to search in, or null to
	 *                      search in all source classes
	 * @return the matching member, or null if there is no match
	 */
	public Member resolveMember(Iterable<? extends ClassDef> sourceClasses, String searchClass) {
		switch (query.kind) {
		case METHOD:
			return resolveMethod(sourceClasses, searchClass);
		case FIELD:
			return resolveField(sourceClasses, searchClass);
		case CLASS:
			return (Member) resolveClass(sourceClasses, searchClass);
		default:
			// The kind is not determined by the query; try the members first.
			Member member = resolveMethod(sourceClasses, searchClass);
			if (member == null) member = resolveField(sourceClasses, searchClass);
			return member;
		}
	}

	/**
	 * Resolve a class target from either a class query or a member query.
	 * Member queries are useful when the class itself is obfuscated but one of
	 * its methods has a stable signature or literal.
	 */
	public ClassDef resolveClassTarget(Iterable<? extends ClassDef> sourceClasses, String searchClass) {
		if (query.kind == FinderQuery.Kind.METHOD || query.kind == FinderQuery.Kind.FIELD) {
			Member member = resolveMember(sourceClasses, searchClass);
			if (member == null) return null;
			for (ClassDef classDef : sourceClasses) {
				if (classDef.getType().equals(member.getDefiningClass())) return classDef;
			}
			return null;
		}
		return resolveClass(sourceClasses, searchClass);
	}

	/**
	 * Resolve the descriptor of the class in which the search must be
	 * performed.
	 *
	 * @param spec         the finder specification
	 * @param defaultClass the class of the patch item, as a descriptor
	 * @return the class descriptor to search in, or null to search everywhere
	 */
	public static String resolveSearchClass(FinderSpec spec, String defaultClass) {
		if (spec.getClassName() == null) return defaultClass;
		return TypeName.toClassDescriptor(spec.getClassName());
	}

}
