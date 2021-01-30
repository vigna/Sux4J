/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2021 Sebastiano Vigna
 *
 * This program and the accompanying materials are made available under the
 * terms of the GNU Lesser General Public License v2.1 or later,
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html,
 * or the Apache Software License 2.0, which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later OR Apache-2.0
 */

package it.unimi.dsi.sux4j.test;

import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class GenerateRandom32BitStrings {
	public static final Logger LOGGER = LoggerFactory.getLogger(GenerateRandom32BitStrings.class);

	public static void main(final String[] arg) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP(GenerateRandom32BitStrings.class.getName(), "Generates a list of sorted 32-bit random strings using only characters in the ISO-8859-1 printable range [32..256).",
				new Parameter[] {
					new FlaggedOption("gap", JSAP.INTSIZE_PARSER, "1", JSAP.NOT_REQUIRED, 'g', "gap", "Impose a minimum gap."),
					new UnflaggedOption("n", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of strings (too small values might cause overflow)."),
					new UnflaggedOption("output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final int n = jsapResult.getInt("n");
		final String output = jsapResult.getString("output");
		final int gap = jsapResult.getInt("gap");

		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.expectedUpdates = n;
		pl.start("Generating... ");

		double l = 0, t;
		final double limit = Math.pow(224, 4);
		final int incr = (int)Math.floor(1.99 * (limit / n)) - 1;

		LOGGER.info("Increment: " + incr);

		@SuppressWarnings("resource")
		final FastBufferedOutputStream fbs = new FastBufferedOutputStream(new FileOutputStream(output));
		final int[] b = new int[4];

		for(int i = 0; i < n; i++) {
			t = (l += (r.nextInt(incr) + gap));
			if (l >= limit) throw new AssertionError(Integer.toString(i));
			for(int j = 4; j-- != 0;) {
				b[j] = (int)(t % 224 + 32);
				t = Math.floor(t / 224);
			}

			for(int j = 0; j < 4; j++) fbs.write(b[j]);
			fbs.write(10);

			pl.lightUpdate();
		}


		pl.done();
		fbs.close();

		LOGGER.info("Last/limit: " + (l / limit));
	}
}
