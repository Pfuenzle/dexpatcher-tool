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

import lanchon.dexpatcher.core.util.TypeName;

import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Member;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;

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
