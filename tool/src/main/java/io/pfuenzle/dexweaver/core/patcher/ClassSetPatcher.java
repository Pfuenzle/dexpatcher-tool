/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi, 2026 Pfuenzle
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package io.pfuenzle.dexweaver.core.patcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.pfuenzle.dexweaver.core.Action;
import io.pfuenzle.dexweaver.core.finder.ClassAnalysis;
import io.pfuenzle.dexweaver.core.finder.FinderQueryException;
import io.pfuenzle.dexweaver.core.finder.FinderResolver;
import io.pfuenzle.dexweaver.core.finder.FinderSpec;
import io.pfuenzle.dexweaver.core.Context;
import io.pfuenzle.dexweaver.core.Marker;
import io.pfuenzle.dexweaver.core.PatchException;
import io.pfuenzle.dexweaver.core.PatcherAnnotation;
import io.pfuenzle.dexweaver.core.model.BasicClassDef;
import io.pfuenzle.dexweaver.core.util.DexUtils;
import io.pfuenzle.dexweaver.core.util.Id;
import io.pfuenzle.dexweaver.core.util.InvalidTypeDescriptorException;
import io.pfuenzle.dexweaver.core.util.Label;
import io.pfuenzle.dexweaver.core.util.SimpleTypeRewriter;
import io.pfuenzle.dexweaver.core.util.Target;

import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;

import static io.pfuenzle.dexweaver.core.PatcherAnnotation.*;
import static io.pfuenzle.dexweaver.core.logger.Logger.Level.*;

// TODO: Warn about changes in superclass and interfaces.

public class ClassSetPatcher extends AnnotatableSetPatcher<ClassDef> {

	public ClassSetPatcher(Context context) {
		super(context);
	}

	// Logging

	@Override
	protected void clearLogPrefix() {
		super.clearLogPrefix();
		setSourceFileClass(null);
	}

	@Override
	protected void setupLogPrefix(String id, ClassDef item, ClassDef patch, ClassDef patched) {
		setupLogPrefix(getItemLabel() + " '" + Label.ofClass(item) + "'");
		setSourceFileClass(patch);
	}

	// Implementation

	@Override
	protected final String getId(ClassDef item) {
		return Id.ofClass(item);
	}

	@Override
	protected String getItemLabel() {
		return "type";
	}

	@Override
	protected int getAccessFlags(ClassDef item) {
		return DexUtils.getClassAccessFlags(item);
	}

	@Override
	protected Action getDefaultAction(String patchId, ClassDef patch) {
		return Action.ADD;
	}

	@Override
	protected void onPrepare(String patchId, ClassDef patch, PatcherAnnotation annotation) throws PatchException {
		if (annotation.getRecursive()) throw invalidElement(Marker.ELEM_RECURSIVE);
		if (annotation.getStaticConstructorAction() == Action.WRAP) {
			throw new PatchException("invalid static constructor action (wrap)");
		}
	}

	@Override
	protected String getTargetId(String patchId, ClassDef patch, PatcherAnnotation annotation)
			throws InvalidTypeDescriptorException, PatchException {
		String target = annotation.getTarget();
		String targetClass = annotation.getTargetClass();
		String targetId =
				(target != null) ? Id.ofClass(Target.resolveClassDescriptor(patch.getType(), target)) :
				(targetClass != null) ? Id.ofClass(targetClass) :
				(annotation.getFinder() != null) ? resolveFinderTargetId(patchId, patch, annotation) :
				patchId;
		if (shouldLogTarget(patchId, targetId)) {
			extendLogPrefixWithTargetLabel(Label.fromClassId(targetId));
		}
		return targetId;
	}

	/**
	 * Resolve the class to patch from a finder annotation.
	 *
	 * <p>The class found by the finder keeps its own name and type; only its
	 * content is patched.
	 */
	private String resolveFinderTargetId(String patchId, ClassDef patch, PatcherAnnotation annotation)
			throws PatchException {
		FinderSpec spec = annotation.getFinder();
		FinderResolver resolver;
		try {
			resolver = new FinderResolver(spec);
		} catch (FinderQueryException e) {
			throw new PatchException("invalid finder query (" + e.getMessage() + ")");
		}
		Collection<ClassDef> sourceClasses = getSourceMap() != null
				? new ArrayList<>(getSourceMap().values())
				: Collections.<ClassDef>emptyList();
		String searchClass = (resolver.getQuery().kind == io.pfuenzle.dexweaver.core.finder.FinderQuery.Kind.METHOD
				|| resolver.getQuery().kind == io.pfuenzle.dexweaver.core.finder.FinderQuery.Kind.FIELD)
				? null : FinderResolver.resolveSearchClass(spec, patch.getType());
		ClassDef found = resolver.resolveClassTarget(sourceClasses, searchClass);
		if (found == null) {
			throw new PatchException("finder target not found (" + spec + ")");
		}
		log(INFO, "finder target '" + found.getType() + "'");
		return Id.ofClass(found.getType());
	}

	@Override
	protected ClassDef onSimpleAdd(ClassDef patch, PatcherAnnotation annotation) {

		// Avoid creating a new object if not necessary.
		if (patch.getAnnotations() == annotation.getFilteredAnnotations()) {
			return patch;
		}

		return new BasicClassDef(
				patch.getType(),
				patch.getAccessFlags(),
				patch.getSuperclass(),
				patch.getInterfaces(),
				patch.getSourceFile(),
				annotation.getFilteredAnnotations(),
				patch.getStaticFields(),
				patch.getInstanceFields(),
				patch.getDirectMethods(),
				patch.getVirtualMethods());

	}

	@Override
	protected ClassDef onSimpleEdit(ClassDef patch, PatcherAnnotation annotation, ClassDef target, boolean inPlace) {

		ClassDef source;
		Set<? extends Annotation> annotations;
		// The type of the class being patched, before any renaming; used as the
		// default search space of finder annotations.
		String targetType = target.getType();
		if (annotation.getContentOnly()) {
			source = target;
			annotations = target.getAnnotations();
			if (!inPlace) patch = SimpleTypeRewriter.renameClass(patch, target.getType());
		} else {
			// Log class access flags before processing members.
			super.onSimpleEdit(patch, annotation, target, inPlace);
			boolean finderTarget = annotation.getFinder() != null;
			if (finderTarget && !inPlace) patch = SimpleTypeRewriter.renameClass(patch, targetType);
			source = patch;
			annotations = annotation.getFilteredAnnotations();
			if (!inPlace && !finderTarget) target = SimpleTypeRewriter.renameClass(target, patch.getType());
		}

		FieldSetPatcher fieldPatcher = new FieldSetPatcher(this, annotation);
		MethodSetPatcher methodPatcher = new MethodSetPatcher(this, annotation);
		setFinderContext(fieldPatcher, methodPatcher, target, targetType);

		String outputSuperclass = annotation.getFinder() != null
				? target.getSuperclass() : source.getSuperclass();
		List<String> outputInterfaces = new ArrayList<>();
		for (CharSequence iface : annotation.getFinder() != null
				? target.getInterfaces() : source.getInterfaces()) {
			outputInterfaces.add(iface.toString());
		}
		return new BasicClassDef(
				source.getType(),
				source.getAccessFlags(),
				outputSuperclass,
				outputInterfaces,
				source.getSourceFile(),
				annotations,
				Collections.unmodifiableCollection(fieldPatcher
						.process(target.getFields(), patch.getFields())),
				Collections.unmodifiableCollection(methodPatcher
						.process(target.getMethods(), patch.getMethods())));

	}

	/**
	 * Give the member patchers access to the source classes and to the
	 * analysis of the class being patched, so that finder annotations can be
	 * resolved.
	 */
	private void setFinderContext(FieldSetPatcher fieldPatcher, MethodSetPatcher methodPatcher,
			ClassDef target, String targetType) {
		Collection<ClassDef> sourceClasses = getSourceMap() != null
				? new ArrayList<>(getSourceMap().values())
				: Collections.<ClassDef>emptyList();
		ClassAnalysis analysis = new ClassAnalysis(target, target.getFields());
		fieldPatcher.setFinderContext(sourceClasses, analysis, targetType);
		methodPatcher.setFinderContext(sourceClasses, analysis, targetType);
	}

	@Override
	protected ClassDef onSimpleReplace(ClassDef patch, PatcherAnnotation annotation, ClassDef target, boolean inPlace) {

		ClassDef source;
		Set<? extends Annotation> annotations;
		if (annotation.getContentOnly()) {
			source = target;
			annotations = target.getAnnotations();
			if (!inPlace) patch = SimpleTypeRewriter.renameClass(patch, target.getType());
		} else {
			source = patch;
			annotations = annotation.getFilteredAnnotations();
			//if (!inPlace) target = renameClass(target, patch.getType());
		}

		return new BasicClassDef(
				source.getType(),
				source.getAccessFlags(),
				source.getSuperclass(),
				source.getInterfaces(),
				source.getSourceFile(),
				annotations,
				patch.getStaticFields(),
				patch.getInstanceFields(),
				patch.getDirectMethods(),
				patch.getVirtualMethods());

	}

}
