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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import com.google.common.base.Charsets;
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
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.io.FileLinesByteArrayIterable;

public class ByteArrayFunctionSpeedTest {
	private final static int NUM_WARMUPS = 4;
	private final static int NUM_SAMPLES = 11;

	public static void main(final String[] arg) throws IOException, JSAPException, ClassNotFoundException, NoSuchMethodException, SecurityException {

		final SimpleJSAP jsap = new SimpleJSAP(ByteArrayFunctionSpeedTest.class.getName(), "Tests the speed of a function on byte arrays. Sequential tests (the default) read keys from disk, whereas random tests cache keys in a contiguous region of memory. Performs a few warmup repetitions, and then the median of a sample is printed on standard output. The detailed results are logged to standard error.",
				new Parameter[] {
					new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
					new Switch("random", 'r', "random", "Tests a subset of strings cached contiguously in memory."),
						new FlaggedOption("decompressor", JSAP.CLASS_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'd', "decompressor", "Use this extension of InputStream to decompress the strings (e.g., java.util.zip.GZIPInputStream)."),
					new Switch("shuffle", 'S', "shuffle", "Shuffle the subset of strings used for random tests."),
					new FlaggedOption("n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-strings", "The (maximum) number of strings used for random testing."),
					new FlaggedOption("save", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "save", "In case of a random test, save to this file the strings used."),
					new Switch("check", 'c', "check", "Check that each string in the list is mapped to its ordinal position."),
					new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised function."),
					new UnflaggedOption("stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Read strings as byte arrays from this file."),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final boolean zipped = jsapResult.getBoolean("zipped");
		Class<? extends InputStream> decompressor = jsapResult.getClass("decompressor");
		final boolean check = jsapResult.getBoolean("check");
		final boolean shuffle = jsapResult.getBoolean("shuffle");
		final boolean random = jsapResult.getBoolean("random");
		final String save = jsapResult.getString("save");
		final int maxStrings = jsapResult.getInt("n");

		if (zipped && decompressor != null) throw new IllegalArgumentException("The zipped and decompressor options are incompatible");
		if (zipped) decompressor = GZIPInputStream.class;
		if (check && random) throw new IllegalArgumentException("You cannot perform checks in random tests");
		if (shuffle && !random) throw new IllegalArgumentException("You can shuffle random tests only");
		if (jsapResult.userSpecified("n") && ! random) throw new IllegalArgumentException("The number of string is meaningful for random tests only");
		if (save != null && ! random) throw new IllegalArgumentException("You can save test string only for random tests");

		@SuppressWarnings("unchecked")
		final Object2LongFunction<byte[]> function = (Object2LongFunction<byte[]>)BinIO.loadObject(functionName);
		final FileLinesByteArrayIterable lines = new FileLinesByteArrayIterable(stringFile, decompressor);
		final long size = lines.size64();

		if (random) {
			final int n = (int)Math.min(maxStrings, size);
			byte[][] test = new byte[n][];
			final int step = (int)(size / n) - 1;
			final Iterator<byte[]> iterator = lines.iterator();
			for(int i = 0; i < n; i++) {
				test[i] = iterator.next().clone();
				for(int j = step; j-- != 0;) iterator.next();
			}
			if (shuffle) Collections.shuffle(Arrays.asList(test));

			if (save != null) {
				final PrintStream ps = new PrintStream(save, Charsets.ISO_8859_1.toString());
				for(final byte[] s: test) ps.println(new String(s, Charsets.ISO_8859_1));
				ps.close();
			}

			final int[] length = new int[n];
			int totalLength = 0;
			for(int i = 0; i < n; i++) totalLength += (length[i] = test[i].length);
			final byte[] a = new byte[totalLength];
			for(int i = 0, s = 0; i < n; i++) {
				System.arraycopy(test[i], 0, a, s, length[i]);
				s += length[i];
			}

			test = null;
			System.gc();
			System.gc();

			long t = -1;
			final long[] sample = new long[NUM_SAMPLES];
			System.err.println("Warmup...");
			for(int k = NUM_WARMUPS + NUM_SAMPLES; k-- != 0;) {
				long time = -System.nanoTime();
				for(int i = 0, s = 0; i < n; i++) {
					t ^= function.getLong(Arrays.copyOfRange(a, s, s += length[i]));
					if ((i & 0xFFFFF) == 0) System.err.print('.');
				}
				System.err.println();
				time += System.nanoTime();
				if (k < NUM_SAMPLES) sample[k] = time;
				System.err.println(Util.format(time / 1E9) + "s, " + Util.format((double)time / n) + " ns/item");
				if (k == NUM_SAMPLES) System.err.println("Sampling " + n + " strings...");
			}
			LongArrays.quickSort(sample);
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
				final Iterator<byte[]> iterator = lines.iterator();

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
				if (k == NUM_SAMPLES) System.err.println("Scanning " + size + " strings...");
			}
			LongArrays.quickSort(sample);
			System.out.println("Median: " + Util.format(sample[NUM_SAMPLES / 2] / 1E9) + "s, " + Util.format(sample[NUM_SAMPLES / 2] / (double)size) + " ns/item");
			if (t == 0) System.err.println(t);
		}
	}
}
