/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2020 Sebastiano Vigna
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
import java.math.BigInteger;

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

public class GenerateRandom64BitStrings {
	public static final Logger LOGGER = LoggerFactory.getLogger(GenerateRandom64BitStrings.class);

	public static void main(final String[] arg) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP(GenerateRandom64BitStrings.class.getName(), "Generates a list of sorted 64-bit random strings using only characters in the ISO-8859-1 printable range [32..256).",
				new Parameter[] {
					new FlaggedOption("repeat", JSAP.INTSIZE_PARSER, "1", JSAP.NOT_REQUIRED, 'r', "repeat", "Repeat each byte this number of times"),
					new FlaggedOption("gap", JSAP.INTSIZE_PARSER, "1", JSAP.NOT_REQUIRED, 'g', "gap", "Impose a minimum gap."),
					new UnflaggedOption("n", JSAP.LONG_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of strings (too small values might cause overflow)."),
					new UnflaggedOption("output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final long n = jsapResult.getLong("n");
		final int repeat = jsapResult.getInt("repeat");
		final int gap = jsapResult.getInt("gap");
		final String output = jsapResult.getString("output");

		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.expectedUpdates = n;
		pl.start("Generating... ");

		BigInteger l = BigInteger.ZERO, t;
		final BigInteger limit = BigInteger.valueOf(224).pow(8);
		final long incr = (long)Math.floor(1.99 * (limit.divide(BigInteger.valueOf(n)).longValue())) - 1;

		@SuppressWarnings("resource")
		final FastBufferedOutputStream fbs = new FastBufferedOutputStream(new FileOutputStream(output));
		final BigInteger divisor = BigInteger.valueOf(224);

		LOGGER.info("Increment: " + incr);

		BigInteger a[];
		final int[] b = new int[8];

		for(long i = 0; i < n; i++) {
			l = l.add(BigInteger.valueOf((r.nextLong() & 0x7FFFFFFFFFFFFFFFL) % incr + gap));
			t = l;
			if (l.compareTo(limit) >= 0) throw new AssertionError(Long.toString(i));
			for(int j = 8; j-- != 0;) {
				a = t.divideAndRemainder(divisor);
				b[j] = a[1].intValue() + 32;
				assert b[j] < 256;
				assert b[j] >= 32;
				t = a[0];
			}

			for(int j = 0; j < 8; j++)
				for(int k = repeat; k-- != 0;)
					fbs.write(b[j]);
			fbs.write(10);
			pl.lightUpdate();
		}


		pl.done();
		fbs.close();

		LOGGER.info("Last/limit: " + (l.doubleValue() / limit.doubleValue()));
	}
}
