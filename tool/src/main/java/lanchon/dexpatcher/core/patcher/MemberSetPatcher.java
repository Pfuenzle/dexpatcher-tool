/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi
 * Copyright 2026 Pfuenzle
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package lanchon.dexpatcher.core.patcher;

import lanchon.dexpatcher.core.Action;
import lanchon.dexpatcher.core.Marker;
import lanchon.dexpatcher.core.PatchException;
import lanchon.dexpatcher.core.PatcherAnnotation;
import lanchon.dexpatcher.core.finder.ClassAnalysis;
import lanchon.dexpatcher.core.finder.FinderQueryException;
import lanchon.dexpatcher.core.finder.FinderResolver;
import lanchon.dexpatcher.core.finder.FinderSpec;

import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Member;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static lanchon.dexpatcher.core.PatcherAnnotation.*;
import static lanchon.dexpatcher.core.logger.Logger.Level.*;

public abstract class MemberSetPatcher<T extends Member> extends AnnotatableSetPatcher<T> {

	protected final Action explicitStaticConstructorAction;
	protected final Action resolvedStaticConstructorAction;     // not null
	protected final Action explicitDefaultAction;
	protected final Action resolvedDefaultAction;               // not null

	/**
	 * The source classes, used to resolve the targets of finder annotations.
	 */
	private Collection<ClassDef> sourceClasses;

	/**
	 * The analysis of the class being patched, used to resolve the semantic
	 * filters of finder annotations.
	 */
	private ClassAnalysis classAnalysis;

	/**
	 * The names of the targets resolved by finder annotations, by patch id.
	 *
	 * <p>The patched member must keep the name of the member it replaces, as
	 * the original code invokes the target by its original name.
	 */
	private final Map<String, String> finderTargets = new LinkedHashMap<>();

	public MemberSetPatcher(ClassSetPatcher parent, PatcherAnnotation annotation) {
		super(parent);
		Action defaultAction = Action.NONE;
		Action sca = annotation.getStaticConstructorAction();
		Action da = annotation.getDefaultAction();
		explicitStaticConstructorAction = sca;
		explicitDefaultAction = da;
		resolvedDefaultAction = (da != null) ? da : defaultAction;
		resolvedStaticConstructorAction = (sca != null) ? sca : resolvedDefaultAction;
	}

	// Implementation

	@Override
	protected int getAccessFlags(T item) {
		return item.getAccessFlags();
	}

	@Override
	protected Action getDefaultAction(String patchId, T patch) throws PatchException {
		if (resolvedDefaultAction == Action.NONE) throw new PatchException("no action defined");
		log(INFO, "default " + resolvedDefaultAction.getLabel());
		return resolvedDefaultAction;
	}

	@Override
	protected void onPrepare(String patchId, T patch, PatcherAnnotation annotation) throws PatchException {
		if (annotation.getTargetClass() != null) throw invalidElement(Marker.ELEM_TARGET_CLASS);
		if (annotation.getStaticConstructorAction() != null) throw invalidElement(Marker.ELEM_STATIC_CONSTRUCTOR_ACTION);
		if (annotation.getDefaultAction() != null) throw invalidElement(Marker.ELEM_DEFAULT_ACTION);
		if (annotation.getContentOnly()) throw invalidElement(Marker.ELEM_CONTENT_ONLY);
		if (annotation.getRecursive()) throw invalidElement(Marker.ELEM_RECURSIVE);
	}

	// Finder Support

	/**
	 * Set the source classes and the analysis of the class being patched, so
	 * that finder annotations can be resolved.
	 */
	public void setFinderContext(Collection<ClassDef> sourceClasses, ClassAnalysis classAnalysis) {
		this.sourceClasses = sourceClasses;
		this.classAnalysis = classAnalysis;
	}

	protected Collection<ClassDef> getSourceClasses() {
		return sourceClasses;
	}

	protected ClassAnalysis getClassAnalysis() {
		return classAnalysis;
	}

	/**
	 * Resolve the name of the target member described by the finder annotation.
	 *
	 * @param annotation the annotation of the patch item
	 * @param patchClass the type of the patch item, as a descriptor
	 * @return the name of the target member, or null if the annotation has no
	 *         finder
	 * @throws PatchException if the finder cannot be resolved
	 */
	protected String resolveFinderTarget(String patchId, PatcherAnnotation annotation, String patchClass)
			throws PatchException {
		FinderSpec spec = annotation.getFinder();
		if (spec == null) return null;
		FinderResolver resolver;
		try {
			resolver = new FinderResolver(spec);
		} catch (FinderQueryException e) {
			throw new PatchException("invalid finder query (" + e.getMessage() + ")");
		}
		resolver.getMatcher().setAnalysis(classAnalysis);
		String searchClass = FinderResolver.resolveSearchClass(spec, patchClass);
		Member target = resolver.resolveMember(sourceClasses, searchClass);
		if (target == null) {
			throw new PatchException("finder target not found (" + spec + ")");
		}
		log(INFO, "finder target '" + target.getName() + "'");
		finderTargets.put(patchId, target.getName());
		return target.getName();
	}

	/**
	 * The name of the target resolved by the finder annotation of the given
	 * patch, or null if the patch has no finder annotation.
	 */
	protected String getFinderTargetName(String patchId) {
		return finderTargets.get(patchId);
	}

}
