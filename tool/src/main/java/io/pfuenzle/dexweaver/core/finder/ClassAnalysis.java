/*
 * DexWeaver - Copyright 2026 Pfuenzle
 * (GNU General Public License version 3 or later)
 *
 * DexWeaver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DexWeaver is a fork of DexPatcher (https://github.com/DexPatcher/dexpatcher-tool)
 * by Rodrigo Balerdi (Lanchon), which is licensed under the same terms.
 */

package io.pfuenzle.dexweaver.core.finder;

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
	private final Map<String, String> fieldToLabel = new LinkedHashMap<>();
	private final Set<String> fieldNames = new LinkedHashSet<>();

	public ClassAnalysis(ClassDef classDef) {
		this(classDef.getType(), classDef.getMethods(), classDef.getFields());
	}

	public ClassAnalysis(ClassDef classDef, Iterable<? extends org.jf.dexlib2.iface.Field> fields) {
		this(classDef.getType(), classDef.getMethods(), fields);
	}

	public ClassAnalysis(String classType, Iterable<? extends Method> methods) {
		this(classType, methods, java.util.Collections.<org.jf.dexlib2.iface.Field>emptyList());
	}

	public ClassAnalysis(String classType, Iterable<? extends Method> methods,
			Iterable<? extends org.jf.dexlib2.iface.Field> fields) {
		this.classType = classType;
		for (org.jf.dexlib2.iface.Field field : fields) {
			fieldNames.add(field.getName());
		}
		for (Method method : methods) {
			List<String> readFields = readFields(method);
			List<String> literals = readLiterals(method);
			if (readFields.isEmpty() && literals.isEmpty()) continue;
			methodFields.put(key(method), readFields);
			methodLiterals.put(key(method), literals);
			mapLiteralsToFields(method, readFields, literals);
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
			fieldToLabel.put(field, label);
		}
	}

	/**
	 * The semantic name of a field, as derived from the label used by the
	 * generated {@code toString()} method.
	 *
	 * <p>A label such as {@code "EntitlementStatus(isPro="} or {@code ", isPlus="}
	 * yields the name {@code isPro} or {@code isPlus}. This is how the
	 * obfuscated fields of a Kotlin data class regain their meaning.
	 */
	public String getSemanticName(String fieldName) {
		String label = fieldToLabel.get(fieldName);
		if (label == null) return null;
		return extractSemanticName(label);
	}

	/**
	 * The field behind a semantic name, or null if there is no such field.
	 */
	public String getFieldForSemanticName(String name) {
		for (Map.Entry<String, String> entry : fieldToLabel.entrySet()) {
			if (name.equals(extractSemanticName(entry.getValue()))) return entry.getKey();
		}
		return null;
	}

	/**
	 * The names of the fields of the analyzed class.
	 */
	public Set<String> getFieldNames() {
		return fieldNames;
	}

	/**
	 * Extract the semantic name of a label such as
	 * {@code "EntitlementStatus(isPro="} or {@code ", isPlus="}.
	 */
	private static String extractSemanticName(String label) {
		int index = label.indexOf('=');
		if (index < 0) return null;
		String name = label.substring(0, index);
		int paren = name.lastIndexOf('(');
		if (paren >= 0) name = name.substring(paren + 1);
		name = name.trim();
		int comma = name.lastIndexOf(',');
		if (comma >= 0) name = name.substring(comma + 1).trim();
		return name.isEmpty() ? null : name;
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
	 *
	 * <p>The lookup is tolerant: the literal may be the full label of the
	 * generated {@code toString()} method, such as
	 * {@code "EntitlementStatus(isPro="}, or just its tail, such as
	 * {@code "isPro="}. A literal without an equals sign is interpreted as a
	 * semantic name of a field.
	 */
	public String getFieldForLiteral(String literal) {
		if (literal == null) return null;
		String field = literalToField.get(literal);
		if (field != null) return field;
		// The label of the first field also carries the class name, as in
		// "EntitlementStatus(isPro="; match on the tail of the label.
		for (Map.Entry<String, String> entry : literalToField.entrySet()) {
			if (entry.getKey().endsWith(literal)) return entry.getValue();
		}
		// A literal without an equals sign is a semantic field name.
		if (!literal.contains("=")) return getFieldForSemanticName(literal);
		return null;
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
