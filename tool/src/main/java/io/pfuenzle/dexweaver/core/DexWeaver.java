/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package io.pfuenzle.dexweaver.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import io.pfuenzle.dexweaver.core.model.BasicDexFile;
import io.pfuenzle.dexweaver.core.patcher.PackagePatcher;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;

public class DexWeaver {

	public static DexFile process(Context context, DexFile sourceDex, DexFile patchDex) {
		return process(context, sourceDex, patchDex, sourceDex.getOpcodes());
	}

	public static DexFile process(Context context, DexFile sourceDex, DexFile patchDex, Opcodes opcodes) {
		Set<? extends ClassDef> sourceClasses = sourceDex.getClasses();
		Set<? extends ClassDef> patchClasses = patchDex.getClasses();
		PackagePatcher patcher = new PackagePatcher(context);
		Collection<ClassDef> patchedClasses = patcher.process(sourceClasses, sourceClasses.size(),
				patchClasses, patchClasses.size());
		return new BasicDexFile(opcodes, Collections.unmodifiableSet(new LinkedHashSet<>(patchedClasses)));
	}

	private DexWeaver() {}

}
