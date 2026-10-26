/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi, 2026 Pfuenzle
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package lanchon.dexpatcher.core.patcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import lanchon.dexpatcher.core.Action;
import lanchon.dexpatcher.core.finder.ClassAnalysis;
import lanchon.dexpatcher.core.finder.FinderQueryException;
import lanchon.dexpatcher.core.finder.FinderResolver;
import lanchon.dexpatcher.core.finder.FinderSpec;
import lanchon.dexpatcher.core.Context;
import lanchon.dexpatcher.core.Marker;
import lanchon.dexpatcher.core.PatchException;
import lanchon.dexpatcher.core.PatcherAnnotation;
import lanchon.dexpatcher.core.model.BasicClassDef;
import lanchon.dexpatcher.core.util.DexUtils;
import lanchon.dexpatcher.core.util.Id;
import lanchon.dexpatcher.core.util.InvalidTypeDescriptorException;
import lanchon.dexpatcher.core.util.Label;
import lanchon.dexpatcher.core.util.SimpleTypeRewriter;
import lanchon.dexpatcher.core.util.Target;

import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.ClassDef;

import static lanchon.dexpatcher.core.PatcherAnnotation.*;
import static lanchon.dexpatcher.core.logger.Logger.Level.*;

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
		String searchClass = FinderResolver.resolveSearchClass(spec, patch.getType());
		ClassDef found = resolver.resolveClass(sourceClasses, searchClass);
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
		if (annotation.getContentOnly()) {
			source = target;
			annotations = target.getAnnotations();
			if (!inPlace) patch = SimpleTypeRewriter.renameClass(patch, target.getType());
		} else {
			// Log class access flags before processing members.
			super.onSimpleEdit(patch, annotation, target, inPlace);
			source = patch;
			annotations = annotation.getFilteredAnnotations();
			if (!inPlace) target = SimpleTypeRewriter.renameClass(target, patch.getType());
		}

		FieldSetPatcher fieldPatcher = new FieldSetPatcher(this, annotation);
		MethodSetPatcher methodPatcher = new MethodSetPatcher(this, annotation);
		setFinderContext(fieldPatcher, methodPatcher, target);

		return new BasicClassDef(
				source.getType(),
				source.getAccessFlags(),
				source.getSuperclass(),
				source.getInterfaces(),
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
			ClassDef target) {
		Collection<ClassDef> sourceClasses = getSourceMap() != null
				? new ArrayList<>(getSourceMap().values())
				: Collections.<ClassDef>emptyList();
		ClassAnalysis analysis = new ClassAnalysis(target, target.getFields());
		fieldPatcher.setFinderContext(sourceClasses, analysis);
		methodPatcher.setFinderContext(sourceClasses, analysis);
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
