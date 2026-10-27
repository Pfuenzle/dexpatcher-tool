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

import java.util.Set;

import io.pfuenzle.dexweaver.core.Action;
import io.pfuenzle.dexweaver.core.ActionParser;
import io.pfuenzle.dexweaver.core.Marker;
import io.pfuenzle.dexweaver.core.PatchException;
import io.pfuenzle.dexweaver.core.PatcherAnnotation;
import io.pfuenzle.dexweaver.core.util.InvalidTypeDescriptorException;
import io.pfuenzle.dexweaver.core.util.SimpleTypeRewriter;
import io.pfuenzle.dexweaver.core.util.Target;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperAnnotation;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperAnnotationElement;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperClassDef;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperField;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperFieldReference;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperMethod;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperMethodReference;
import io.pfuenzle.dexweaver.transform.util.wrapper.WrapperRewriterModule;

import org.jf.dexlib2.base.value.BaseStringEncodedValue;
import org.jf.dexlib2.iface.Annotatable;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.AnnotationElement;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.RewriterUtils;
import org.jf.dexlib2.rewriter.Rewriters;

public class PatchRewriterModule extends WrapperRewriterModule<RewriterModule> {

	public static RewriterModule of(RewriterModule wrappedModule, String annotationPackage) {
		if (annotationPackage != null) {
			return new PatchRewriterModule(wrappedModule, new ActionParser(annotationPackage));
		} else {
			return wrappedModule;
		}
	}

	protected final ActionParser actionParser;

	public PatchRewriterModule(RewriterModule wrappedModule, ActionParser actionParser) {
		super(wrappedModule);
		this.actionParser = actionParser;
	}

	@Override
	public Rewriter<ClassDef> getClassDefRewriter(final Rewriters rewriters) {
		final Rewriter<ClassDef> wrappedClassDefRewriter = wrappedModule.getClassDefRewriter(rewriters);
		return new Rewriter<ClassDef>() {
			@Override
			public ClassDef rewrite(final ClassDef classDef) {
				PatcherAnnotation annotation = null;
				try {
					annotation = PatcherAnnotation.parse(actionParser, classDef.getAnnotations());
				} catch (PatchException e) {}
				if (annotation == null) return wrappedClassDefRewriter.rewrite(classDef);
				String currentDescriptor = classDef.getType();
				String targetDescriptor = null;
				try {
					String target = annotation.getTarget();
					String targetClass = annotation.getTargetClass();
					targetDescriptor =
							(target != null) ? Target.resolveDescriptor(currentDescriptor, target) :
							(targetClass != null) ? targetClass :
							null;
				} catch (InvalidTypeDescriptorException e) {
					return wrappedClassDefRewriter.rewrite(classDef);
				}
				ClassDef rewrittenClass =
						(targetDescriptor != null && !targetDescriptor.equals(currentDescriptor)) ?
								SimpleTypeRewriter.renameClass(
										wrappedClassDefRewriter.rewrite(
												SimpleTypeRewriter.renameClass(
														classDef, targetDescriptor)), currentDescriptor) :
								wrappedClassDefRewriter.rewrite(
										classDef);
				return new WrapperClassDef(rewrittenClass) {
					@Override
					public Set<? extends Annotation> getAnnotations() {
						return getRewrittenAnnotations(rewriters, classDef, CLASS_ANNOTATION_REWRITER);
					}
				};
			}
		};
	}

	@Override
	public Rewriter<Field> getFieldRewriter(final Rewriters rewriters) {
		final Rewriter<Field> wrappedFieldRewriter = wrappedModule.getFieldRewriter(rewriters);
		return new Rewriter<Field>() {
			@Override
			public Field rewrite(final Field field) {
				return new WrapperField(wrappedFieldRewriter.rewrite(field)) {
					@Override
					public Set<? extends Annotation> getAnnotations() {
						return getRewrittenAnnotations(rewriters, field, FIELD_ANNOTATION_REWRITER);
					}
				};
			}
		};
	}

	@Override
	public Rewriter<Method> getMethodRewriter(final Rewriters rewriters) {
		final Rewriter<Method> wrappedMethodRewriter = wrappedModule.getMethodRewriter(rewriters);
		return new Rewriter<Method>() {
			@Override
			public Method rewrite(final Method method) {
				return new WrapperMethod(wrappedMethodRewriter.rewrite(method)) {
					@Override
					public Set<? extends Annotation> getAnnotations() {
						return getRewrittenAnnotations(rewriters, method, METHOD_ANNOTATION_REWRITER);
					}
				};
			}
		};
	}

	protected interface AnnotationElementValueRewriter<I extends Annotatable> {
		EncodedValue rewrite(Rewriters rewriters, I item, Action action, String elementName, EncodedValue elementValue);
	}

	public <I extends Annotatable> Set<? extends Annotation> getRewrittenAnnotations(final Rewriters rewriters,
			final I item, final AnnotationElementValueRewriter<I> annotationElementValueRewriter) {
		Rewriter<Annotation> annotationRewriter = new Rewriter<Annotation>() {
			@Override
			public Annotation rewrite(final Annotation annotation) {
				final Action action = actionParser.parseTypeDescriptor(annotation.getType());
				Annotation rewrittenAnnotation = rewriters.getAnnotationRewriter().rewrite(annotation);
				if (action == null) return rewrittenAnnotation;
				return new WrapperAnnotation(rewrittenAnnotation) {
					@Override
					public Set<? extends AnnotationElement> getElements() {
						Rewriter<AnnotationElement> annotationElementRewriter = new Rewriter<AnnotationElement>() {
							@Override
							public AnnotationElement rewrite(final AnnotationElement annotationElement) {
								return new WrapperAnnotationElement(
										rewriters.getAnnotationElementRewriter().rewrite(annotationElement)) {
									@Override
									public EncodedValue getValue() {
										return annotationElementValueRewriter.rewrite(rewriters, item, action,
												annotationElement.getName(), annotationElement.getValue());
									}
								};
							}
						};
						return RewriterUtils.rewriteSet(annotationElementRewriter, annotation.getElements());
					}
				};
			}
		};
		return RewriterUtils.rewriteSet(annotationRewriter, item.getAnnotations());
	}

	public static final AnnotationElementValueRewriter<ClassDef> CLASS_ANNOTATION_REWRITER =
			new AnnotationElementValueRewriter<ClassDef>() {
				@Override
				public EncodedValue rewrite(final Rewriters rewriters, final ClassDef classDef, Action action,
						String elementName, final EncodedValue elementValue) {
					switch (elementName) {
						case Marker.ELEM_TARGET:
							return new BaseStringEncodedValue() {
								@Override
								public String getValue() {
									String target = ((StringEncodedValue) elementValue).getValue();
									if (target.isEmpty()) return target;
									String targetDescriptor;
									try {
										targetDescriptor = Target.resolveDescriptor(classDef.getType(), target);
									} catch (InvalidTypeDescriptorException e) {
										return target;
									}
									return rewriters.getTypeRewriter().rewrite(targetDescriptor);
								}
							};
						default:
							return rewriters.getEncodedValueRewriter().rewrite(elementValue);
					}
				}
			};

	public static final AnnotationElementValueRewriter<Field> FIELD_ANNOTATION_REWRITER =
			new AnnotationElementValueRewriter<Field>() {
				@Override
				public EncodedValue rewrite(final Rewriters rewriters, final Field field, Action action,
						String elementName, final EncodedValue elementValue) {
					switch (elementName) {
						case Marker.ELEM_TARGET:
							return new BaseStringEncodedValue() {
								@Override
								public String getValue() {
									final String target = ((StringEncodedValue) elementValue).getValue();
									if (target.isEmpty()) return target;
									FieldReference fieldReference = new WrapperFieldReference(field) {
										@Override
										public String getName() {
											return target;
										}
									};
									return rewriters.getFieldReferenceRewriter().rewrite(fieldReference).getName();
								}
							};
						default:
							return rewriters.getEncodedValueRewriter().rewrite(elementValue);
					}
				}
			};

	public static final AnnotationElementValueRewriter<Method> METHOD_ANNOTATION_REWRITER =
			new AnnotationElementValueRewriter<Method>() {
				@Override
				public EncodedValue rewrite(final Rewriters rewriters, final Method method, Action action,
						String elementName, final EncodedValue elementValue) {
					switch (elementName) {
						case Marker.ELEM_TARGET:
							return new BaseStringEncodedValue() {
								@Override
								public String getValue() {
									final String target = ((StringEncodedValue) elementValue).getValue();
									if (target.isEmpty()) return target;
									MethodReference methodReference = new WrapperMethodReference(method) {
										@Override
										public String getName() {
											return target;
										}
									};
									return rewriters.getMethodReferenceRewriter().rewrite(methodReference).getName();
								}
							};
						default:
							return rewriters.getEncodedValueRewriter().rewrite(elementValue);
					}
				}
			};

}
