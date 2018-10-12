package it.unimi.dsi.sux4j.test;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2018 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */


import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;

public class LongFunctionSpeedTest {
	private final static int NUM_WARMUPS = 4;
	private final static int NUM_SAMPLES = 11;

	public static void main(final String[] arg) throws IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP(LongFunctionSpeedTest.class.getName(), "Tests the speed of a function on longs. Sequential tests (the default) read keys from disk, whereas random tests cache keys in a contiguous region of memory. Performs a few warmup repetitions, and then the median of a sample is printed on standard output. The detailed results are logged to standard error.",
				new Parameter[] {
					new Switch("random", 'r', "random", "Test a subset of longs cached contiguously in memory."),
					new Switch("shuffle", 'S', "shuffle", "Shuffle the subset of longs used for random tests."),
					new FlaggedOption("n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-longs", "The (maximum) number of longs used for random testing."),
					new Switch("check", 'c', "check", "Check that each long in the list is mapped to its ordinal position."),
					new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised function."),
					new UnflaggedOption("longFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Read longs in binary format from this file."),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String longFile = jsapResult.getString("longFile");
		final boolean check = jsapResult.getBoolean("check");
		final boolean shuffle = jsapResult.getBoolean("shuffle");
		final boolean random = jsapResult.getBoolean("random");
		final int maxStrings = jsapResult.getInt("n");

		if (check && random) throw new IllegalArgumentException("You cannot perform checks in random tests");
		if (shuffle && !random) throw new IllegalArgumentException("You can shuffle random tests only");
		if (jsapResult.userSpecified("n") && ! random) throw new IllegalArgumentException("The number of string is meaningful for random tests only");

		@SuppressWarnings("unchecked")
		final Object2LongFunction<Long> function = (Object2LongFunction<Long>)BinIO.loadObject(functionName);
		@SuppressWarnings("resource")
		final LongArrayList lines = LongArrayList.wrap(BinIO.loadLongs(longFile));

		final long size = lines.size();

		if (random) {
			final int n = (int)Math.min(maxStrings, size);
			final long[] test = new long[n];
			final int step = (int)(size / n) - 1;
			final LongIterator iterator = lines.iterator();
			for(int i = 0; i < n; i++) {
				test[i] = iterator.nextLong();
				for(int j = step; j-- != 0;) iterator.nextLong();
			}
			if (shuffle) Collections.shuffle(Arrays.asList(test));

			System.gc();
			System.gc();

			long t = -1;
			final long[] sample = new long[NUM_SAMPLES];
			System.err.println("Warmup...");
			for(int k = NUM_WARMUPS + NUM_SAMPLES; k-- != 0;) {
				long time = -System.nanoTime();
				for(int i = 0; i < n; i++) {
					t ^= function.getLong(Long.valueOf(test[i]));
					if ((i & 0xFFFFF) == 0) System.err.print('.');
				}
				System.err.println();
				time += System.nanoTime();
				if (k < NUM_SAMPLES) sample[k] = time;
				System.err.println(Util.format(time / 1E9) + "s, " + Util.format((double)time / n) + " ns/item");
				if (k == NUM_SAMPLES) System.err.println("Sampling " + n + " longs...");
			}
			System.out.println("Median: " + Util.format(sample[NUM_SAMPLES / 2] / 1E9) + "s, " + Util.format(sample[NUM_SAMPLES / 2] / (double)n) + " ns/item");
			if (t == 0) System.err.println(t);
		}
		else {
			System.gc();
			System.gc();

			long t = -1;
			final long[] sample = new long[NUM_SAMPLES];
			System.err.println("Warmup...");
			for(int k = NUM_WARMUPS + NUM_SAMPLES; k-- != 0;) {
				final Iterator<Long> iterator = lines.iterator();

				long time = -System.nanoTime();
				long index;
				for(long i = 0; i < size; i++) {
					index = function.getLong(iterator.next());
					t ^= index;
					if (check && index != i) throw new AssertionError(index + " != " + i);
					if ((i & 0xFFFFF) == 0) System.err.print('.');
				}
				System.err.println();
				time += System.nanoTime();
				if (k < NUM_SAMPLES) sample[k] = time;
				System.err.println(Util.format(time / 1E9) + "s, " + Util.format((double)time / size) + " ns/item");
				if (k == NUM_SAMPLES) System.err.println("Scanning " + size + " longs...");
			}
			System.out.println("Median: " + Util.format(sample[NUM_SAMPLES / 2] / 1E9) + "s, " + Util.format(sample[NUM_SAMPLES / 2] / (double)size) + " ns/item");
			if (t == 0) System.err.println(t);
		}
	}
}
