/*
 * DexPatcher - Copyright 2015-2026 Pfuenzle
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package lanchon.dexpatcher.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@DexIgnore
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
		ElementType.TYPE,
		ElementType.CONSTRUCTOR,
		ElementType.METHOD,
		ElementType.FIELD
})
public @interface DexFinder {

	// The finder query, using the DexExplore advanced query syntax, for
	// example 'm:public+final, p:com.revenuecat.purchases.CustomerInfo'.
	String value();

	// The class in which to search for the target, as a fully qualified
	// class name. If empty, the class of the annotated patch item is used.
	String className() default "";

}
