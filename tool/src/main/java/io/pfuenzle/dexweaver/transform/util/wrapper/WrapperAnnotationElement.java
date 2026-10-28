/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package io.pfuenzle.dexweaver.transform.util.wrapper;

import com.android.tools.smali.dexlib2.base.BaseAnnotationElement;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;

public class WrapperAnnotationElement extends BaseAnnotationElement {

	protected final AnnotationElement wrappedAnnotationElement;

	public WrapperAnnotationElement(AnnotationElement wrappedAnnotationElement) {
		this.wrappedAnnotationElement = wrappedAnnotationElement;
	}

	@Override
	public String getName() {
		return wrappedAnnotationElement.getName();
	}

	@Override
	public EncodedValue getValue() {
		return wrappedAnnotationElement.getValue();
	}

}