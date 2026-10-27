/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package io.pfuenzle.dexweaver.core.patcher;

import io.pfuenzle.dexweaver.core.Context;
import io.pfuenzle.dexweaver.core.Marker;
import io.pfuenzle.dexweaver.core.PatchException;
import io.pfuenzle.dexweaver.core.PatcherAnnotation;
import io.pfuenzle.dexweaver.core.util.DexUtils;
import io.pfuenzle.dexweaver.core.util.Id;
import io.pfuenzle.dexweaver.core.util.Label;
import io.pfuenzle.dexweaver.core.util.Target;

import org.jf.dexlib2.iface.ClassDef;

import static io.pfuenzle.dexweaver.core.PatcherAnnotation.*;
import static io.pfuenzle.dexweaver.core.logger.Logger.Level.*;

public class PackagePatcher extends ClassSetPatcher {

	private boolean processingPackage;

	public PackagePatcher(Context context) {
		super(context);
	}

	// Implementation

	@Override
	protected void onPrepare(String patchId, ClassDef patch, PatcherAnnotation annotation) throws PatchException {
		processingPackage = DexUtils.isPackageId(patchId);
		if (!processingPackage) {
			super.onPrepare(patchId, patch, annotation);
			return;
		}
		if (annotation.getTargetClass() != null) throw invalidElement(Marker.ELEM_TARGET_CLASS);
		if (annotation.getContentOnly()) throw invalidElement(Marker.ELEM_CONTENT_ONLY);
	}

	@Override
	protected void onReplace(String patchId, ClassDef patch, PatcherAnnotation annotation) throws PatchException {
		if (!processingPackage) {
			super.onReplace(patchId, patch, annotation);
			return;
		}
		String targetId = getPackageTargetId(patchId, patch, annotation);
		boolean recursive = annotation.getRecursive();
		if (isLogging(DEBUG)) log(DEBUG, recursive ? "replace package recursive" : "replace package non-recursive");
		removePackage(targetId, recursive);
		ClassDef patched = onSimpleAdd(patch, annotation);
		addPatched(patch, patched);
	}

	@Override
	protected void onRemove(String patchId, ClassDef patch, PatcherAnnotation annotation) throws PatchException {
		if (!processingPackage) {
			super.onRemove(patchId, patch, annotation);
			return;
		}
		String targetId = getPackageTargetId(patchId, patch, annotation);
		boolean recursive = annotation.getRecursive();
		if (isLogging(DEBUG)) log(DEBUG, recursive ? "remove package recursive" : "remove package non-recursive");
		removePackage(targetId, recursive);
	}

	private String getPackageTargetId(String patchId, ClassDef patch, PatcherAnnotation annotation) throws PatchException {
		String target = annotation.getTarget();
		String targetId = (target != null) ? Id.ofClass(Target.resolvePackageDescriptor(target)) : patchId;
		if (!DexUtils.isPackageId(targetId)) throw new PatchException("target is not a package");
		if (shouldLogTarget(patchId, targetId)) {
			extendLogPrefixWithTargetLabel(Label.fromClassId(targetId));
		}
		return targetId;
	}

	private void removePackage(String targetId, boolean recursive) throws PatchException {
		int prefixLength = targetId.length() - (Marker.NAME_PACKAGE_INFO.length() + 1);
		String prefix = targetId.substring(0, prefixLength);
		for (String id: getSourceMap().keySet()) {
			if (id.startsWith(prefix) && (recursive || id.indexOf('/', prefixLength) < 0) && id.endsWith(";")) {
				try {
					addTarget(id, false);
					if (isLogging(DEBUG)) log(DEBUG, "remove type '" + Label.fromClassId(id) + "'");
				} catch (PatchException e) {
					log(ERROR, "already targeted type '" + Label.fromClassId(id) + "'");
				}
			}
		}
	}

}
