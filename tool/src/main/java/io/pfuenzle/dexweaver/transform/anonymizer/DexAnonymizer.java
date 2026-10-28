/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package io.pfuenzle.dexweaver.transform.anonymizer;

import io.pfuenzle.dexweaver.core.logger.Logger;
import io.pfuenzle.dexweaver.core.util.Label;
import io.pfuenzle.dexweaver.transform.BaseLogger;
import io.pfuenzle.dexweaver.transform.TransformLogger;

import com.android.tools.smali.dexlib2.rewriter.Rewriter;
import com.android.tools.smali.dexlib2.rewriter.RewriterModule;
import com.android.tools.smali.dexlib2.rewriter.Rewriters;

public final class DexAnonymizer extends BaseLogger implements Rewriter<String>, TypeAnonymizer.ErrorHandler {

	private final TypeAnonymizer typeAnonymizer;
	private final Logger.Level infoLevel;
	private final Logger.Level errorLevel;

	public DexAnonymizer(TypeAnonymizer typeAnonymizer, TransformLogger logger, String logPrefix,
			Logger.Level infoLevel, Logger.Level errorLevel) {
		super(logger, logPrefix);
		this.typeAnonymizer = typeAnonymizer;
		this.infoLevel = infoLevel;
		this.errorLevel = errorLevel;
	}

	public RewriterModule getModule() {
		return new RewriterModule() {
			@Override
			public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
				return DexAnonymizer.this;
			}
		};
	}

	@Override
	public String rewrite(String type) {
		String anonymizedType = typeAnonymizer.anonymizeType(type, this);
		if (logger.isLogging(infoLevel) && !anonymizedType.equals(type)) {
			StringBuilder sb = getMessageHeaderForClass(type);
			sb.append(typeAnonymizer.isReanonymizer() ? "reanonymized to '" : "deanonymized to '")
					.append(Label.fromClassDescriptor(anonymizedType)).append("'");
			logger.log(infoLevel, sb.toString());
		}
		return anonymizedType;
	}

	@Override
	public void onError(String type, String message) {
		if (logger.isLogging(errorLevel)) {
			StringBuilder sb = getMessageHeaderForClass(type);
			sb.append(message);
			logger.log(errorLevel, sb.toString());
		}
	}

}
