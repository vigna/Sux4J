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

import org.apache.commons.math3.random.RandomGenerator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class SelectSpeedTest {

	public static void main(final String[] arg) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(SelectSpeedTest.class.getName(), "Tests the speed of rank/select implementations.",
				new Parameter[] {
					new UnflaggedOption("numBits", JSAP.LONGSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of bits."),
					new UnflaggedOption("density", JSAP.DOUBLE_PARSER, ".5", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The density."),
					new FlaggedOption("numPos", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, 'p', "positions", "The number of positions to test"),
					//new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding."),
					//new Switch("zipped", 'z', "zipped", "The term list is compressed in gzip format."),
					//new FlaggedOption("termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read terms from this file (without loading them into core memory) instead of standard input."),
					//new UnflaggedOption("trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final long numBits = jsapResult.getLong("numBits");
		final double density = jsapResult.getDouble("density");
		final int numPos = jsapResult.getInt("numPos");

		final RandomGenerator random = new XoRoShiRo128PlusRandomGenerator(42);
		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(numBits);
		long c = 0;
		for(long i = numBits; i-- != 0;)
			if (random.nextDouble() < density) {
				bitVector.set(i);
				c++;
			}

		final long[] rankPosition = new long[numPos];
		final long[] selectPosition = new long[numPos];

		for(int i = numPos; i-- != 0;) {
			rankPosition[i] = (random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % numBits;
			selectPosition[i] = (random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % c;
		}

		long time;
		final SimpleSelect simpleSelect = new SimpleSelect(bitVector);
		for(int k = 1000; k-- != 0;) {

			System.out.println("=== Simple ===");
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) simpleSelect.select(selectPosition[i]);
			time += System.currentTimeMillis();
			System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/select");

/*			System.out.println("=== Sparse ===");
			SparseSelect sparseSelect = new SparseSelect(bitVector);
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) sparseSelect.select(selectPosition[i]);
			time += System.currentTimeMillis();
	 		System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/select");*/
		}
	}
}
