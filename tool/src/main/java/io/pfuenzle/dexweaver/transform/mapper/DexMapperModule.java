/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package io.pfuenzle.dexweaver.transform.mapper;

import io.pfuenzle.dexweaver.core.util.DexUtils;
import io.pfuenzle.dexweaver.core.util.ElementalTypeRewriter;
import io.pfuenzle.dexweaver.transform.mapper.map.DexMap;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperFieldReference;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperMethodReference;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperRewriterModule;

import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.rewriter.Rewriter;
import com.android.tools.smali.dexlib2.rewriter.RewriterModule;
import com.android.tools.smali.dexlib2.rewriter.Rewriters;

public class DexMapperModule extends WrapperRewriterModule<RewriterModule> {

	protected final DexMap dexMap;

	public DexMapperModule(DexMap dexMap) {
		this(dexMap, new RewriterModule());
	}

	public DexMapperModule(DexMap dexMap, RewriterModule wrappedModule) {
		super(wrappedModule);
		this.dexMap = dexMap;
	}

	@Override
	public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
		final Rewriter<String> wrappedTypeRewriter = wrappedModule.getTypeRewriter(rewriters);
		return new ElementalTypeRewriter() {
			@Override
			public String rewriteElementalType(String type) {
				if (DexUtils.isClassDescriptor(type)) {
					String mapping = dexMap.getClassMapping(type);
					if (mapping != null) return mapping;
				}
				// WARNING: This routes the elemental type to the wrapped rewriter.
				return wrappedTypeRewriter.rewrite(type);
			}
		};
	}

	@Override
	public Rewriter<FieldReference> getFieldReferenceRewriter(Rewriters rewriters) {
		final Rewriter<FieldReference> wrappedFieldReferenceRewriter =
				wrappedModule.getFieldReferenceRewriter(rewriters);
		return new Rewriter<FieldReference>() {
			@Override
			public FieldReference rewrite(FieldReference field) {
				FieldReference rewrittenField = wrappedFieldReferenceRewriter.rewrite(field);
				final String mapping = dexMap.getFieldMapping(field);
				if (mapping != null) {
					return new WrapperFieldReference(rewrittenField) {
						@Override
						public String getName() {
							return mapping;
						}
					};
				}
				return rewrittenField;
			}
		};
	}

	@Override
	public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters rewriters) {
		final Rewriter<MethodReference> wrappedMethodReferenceRewriter =
				wrappedModule.getMethodReferenceRewriter(rewriters);
		return new Rewriter<MethodReference>() {
			@Override
			public MethodReference rewrite(MethodReference method) {
				MethodReference rewrittenMethod = wrappedMethodReferenceRewriter.rewrite(method);
				final String mapping = dexMap.getMethodMapping(method);
				if (mapping != null) {
					return new WrapperMethodReference(rewrittenMethod) {
						@Override
						public String getName() {
							return mapping;
						}
					};
				}
				return rewrittenMethod;
			}
		};
	}

}
