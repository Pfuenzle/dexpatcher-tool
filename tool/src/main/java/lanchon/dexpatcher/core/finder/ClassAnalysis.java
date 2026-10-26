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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.StringReference;

/**
 * Extracts the fields read and the string literals used by the methods of a
 * class.
 *
 * <p>This is used to resolve the semantic meaning of obfuscated members. For
 * example, the generated {@code toString()} method of a Kotlin data class
 * reads the fields in declaration order and labels them with string literals
 * such as {@code "isPro="}, which allows the field behind the obfuscated name
 * {@code a} to be identified.
 */
public final class ClassAnalysis {

	private final String classType;
	private final Map<String, List<String>> methodFields = new LinkedHashMap<>();
	private final Map<String, List<String>> methodLiterals = new LinkedHashMap<>();
	private final Map<String, String> literalToField = new LinkedHashMap<>();

	public ClassAnalysis(ClassDef classDef) {
		this(classDef.getType(), classDef.getMethods());
	}

	public ClassAnalysis(String classType, Iterable<? extends Method> methods) {
		this.classType = classType;
		for (Method method : methods) {
			List<String> fields = readFields(method);
			List<String> literals = readLiterals(method);
			if (fields.isEmpty() && literals.isEmpty()) continue;
			methodFields.put(key(method), fields);
			methodLiterals.put(key(method), literals);
			mapLiteralsToFields(method, fields, literals);
		}
	}

	/**
	 * The type of the analyzed class, as a descriptor.
	 */
	public String getClassType() {
		return classType;
	}

	/**
	 * Associate the labelled literals of a method with the fields it reads.
	 *
	 * <p>Only literals that look like a label (they contain an equals sign) are
	 * taken into account, and they are paired with the fields in the order in
	 * which they are read.
	 */
	private void mapLiteralsToFields(Method method, List<String> fields, List<String> literals) {
		List<String> labels = new ArrayList<>();
		for (String literal : literals) {
			if (literal.contains("=")) labels.add(literal);
		}
		if (labels.isEmpty() || labels.size() != fields.size()) return;
		for (int i = 0; i < labels.size(); i++) {
			String label = labels.get(i);
			String field = fields.get(i);
			String previous = literalToField.put(label, field);
			if (previous != null && !previous.equals(field)) {
				// Conflicting associations; keep the first one.
				literalToField.put(label, previous);
			}
		}
	}

	// Public API

	/**
	 * The fields read by the given method, in the order of first read.
	 */
	public List<String> getReadFields(Method method) {
		List<String> fields = methodFields.get(key(method));
		return fields != null ? fields : new ArrayList<String>();
	}

	/**
	 * The string literals used by the given method, in order of appearance.
	 */
	public List<String> getLiterals(Method method) {
		List<String> literals = methodLiterals.get(key(method));
		return literals != null ? literals : new ArrayList<String>();
	}

	/**
	 * The field associated with the given literal, or null if the literal is
	 * not associated with any field.
	 */
	public String getFieldForLiteral(String literal) {
		return literalToField.get(literal);
	}

	/**
	 * All the known literal to field associations.
	 */
	public Map<String, String> getLiteralToField() {
		return literalToField;
	}

	// Analysis

	private static String key(Method method) {
		return method.getName() + java.util.Arrays.toString(method.getParameterTypes().toArray())
				+ ':' + method.getReturnType();
	}

	static List<String> readFields(Method method) {
		Set<String> fields = new LinkedHashSet<>();
		if (method.getImplementation() == null) return new ArrayList<>(fields);
		for (Instruction instruction : method.getImplementation().getInstructions()) {
			if (!(instruction instanceof ReferenceInstruction)) continue;
			Opcode opcode = instruction.getOpcode();
			if (opcode != Opcode.IGET && opcode != Opcode.IGET_WIDE &&
					opcode != Opcode.IGET_OBJECT && opcode != Opcode.IGET_BOOLEAN &&
					opcode != Opcode.IGET_BYTE && opcode != Opcode.IGET_CHAR &&
					opcode != Opcode.IGET_SHORT) continue;
			FieldReference reference = (FieldReference) ((ReferenceInstruction) instruction).getReference();
			fields.add(reference.getName());
		}
		return new ArrayList<>(fields);
	}

	static List<String> readLiterals(Method method) {
		List<String> literals = new ArrayList<>();
		if (method.getImplementation() == null) return literals;
		for (Instruction instruction : method.getImplementation().getInstructions()) {
			if (instruction.getOpcode() != Opcode.CONST_STRING) continue;
			StringReference reference = (StringReference) ((ReferenceInstruction) instruction).getReference();
			String value = reference.toString();
			if (!literals.contains(value)) literals.add(value);
		}
		return literals;
	}

}
