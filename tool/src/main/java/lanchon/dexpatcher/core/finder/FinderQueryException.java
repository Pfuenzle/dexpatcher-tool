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

/** Thrown when a {@link FinderQuery} string cannot be parsed. */
public final class FinderQueryException extends Exception {

    public FinderQueryException(String message) {
        super(message);
    }

}
