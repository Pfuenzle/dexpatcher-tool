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

/**
 * A parsed {@code @DexFinder} annotation: the finder query and the optional
 * class in which the search must be performed.
 */
public final class FinderSpec {

	private final String query;
	private final String className;

	public FinderSpec(String query, String className) {
		this.query = query;
		this.className = className;
	}

	public String getQuery() {
		return query;
	}

	/** The class to search in, as a fully qualified name, or null if unset. */
	public String getClassName() {
		return className;
	}

	@Override
	public String toString() {
		return (className != null ? className + " " : "") + query;
	}

}
