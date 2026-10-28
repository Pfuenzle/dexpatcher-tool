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

import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;

/**
 * Parses the {@code @DexFinder} annotation of a patch item.
 */
public final class FinderAnnotationParser {

	public static final String FINDER_CLASS_NAME = "DexFinder";

	private static final String ELEM_VALUE = "value";
	private static final String ELEM_CLASS_NAME = "className";

	private final String finderTypeDescriptor;

	public FinderAnnotationParser(String annotationPackage) {
		String className = FINDER_CLASS_NAME;
		if (annotationPackage != null && !annotationPackage.isEmpty()) {
			className = annotationPackage + '.' + className;
		}
		this.finderTypeDescriptor = TypeName.toClassDescriptor(className);
	}

	public String getFinderTypeDescriptor() {
		return finderTypeDescriptor;
	}

	/**
	 * Extract the finder specification from an annotation, if the annotation is
	 * a {@code @DexFinder} annotation.
	 *
	 * @return the finder specification, or null if the annotation is not a finder
	 * @throws FinderQueryException if the annotation is malformed
	 */
	public FinderSpec parse(Annotation annotation) throws FinderQueryException {
		if (annotation == null) return null;
		if (!finderTypeDescriptor.equals(annotation.getType())) return null;
		String query = null;
		String className = null;
		for (AnnotationElement element : annotation.getElements()) {
			String name = element.getName();
			String value = ((StringEncodedValue) element.getValue()).getValue();
			if (ELEM_VALUE.equals(name)) {
				query = value;
			} else if (ELEM_CLASS_NAME.equals(name)) {
				className = value;
			}
		}
		boolean queryEmpty = (query == null || query.isEmpty());
		boolean classNameEmpty = (className == null || className.isEmpty());
		if (queryEmpty && classNameEmpty) {
			throw new FinderQueryException("finder annotation has no query");
		}
		// Validate the query eagerly so that errors surface at parse time.
		if (!queryEmpty) FinderQuery.parse(query);
		if (classNameEmpty) className = null;
		return new FinderSpec(query, className);
	}

}
