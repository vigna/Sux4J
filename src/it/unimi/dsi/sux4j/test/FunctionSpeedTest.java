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
import java.io.PrintStream;
import java.nio.charset.Charset;
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
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.lang.MutableString;

public class FunctionSpeedTest {
	private final static int NUM_SAMPLES = 11;

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP(FunctionSpeedTest.class.getName(), "Tests the speed of a function on character sequences. Sequential tests (the default) read keys from disk, whereas random tests cache keys in a contiguous region of memory. Performs a few warmup repetitions, and then the median of a sample is printed on standard output. The detailed results are logged to standard error.",
				new Parameter[] {
					new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding."),
					new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
					new Switch("random", 'r', "random", "Test a subset of strings cached contiguously in memory."),
					new Switch("shuffle", 'S', "shuffle", "Shuffle the subset of strings used for random tests."),
					new FlaggedOption("n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-strings", "The (maximum) number of strings used for random testing."),
					new FlaggedOption("save", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "save", "In case of a random test, save to this file the strings used."),
					new Switch("check", 'c', "check", "Check that each string in the list is mapped to its ordinal position."),
					new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised function."),
					new UnflaggedOption("stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Read strings from this file."),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean check = jsapResult.getBoolean("check");
		final boolean shuffle = jsapResult.getBoolean("shuffle");
		final boolean random = jsapResult.getBoolean("random");
		final String save = jsapResult.getString("save");
		final int maxStrings = jsapResult.getInt("n");

		if (check && random) throw new IllegalArgumentException("You cannot perform checks in random tests");
		if (shuffle && !random) throw new IllegalArgumentException("You can shuffle random tests only");
		if (jsapResult.userSpecified("n") && ! random) throw new IllegalArgumentException("The number of string is meaningful for random tests only");
		if (save != null && ! random) throw new IllegalArgumentException("You can save test string only for random tests");

		@SuppressWarnings("unchecked")
		final Object2LongFunction<? extends CharSequence> function = (Object2LongFunction<? extends CharSequence>)BinIO.loadObject(functionName);
		final FileLinesCollection flc = new FileLinesCollection(stringFile, encoding.name(), zipped);
		final long size = flc.size();

		if (random) {
			final int n = (int)Math.min(maxStrings, size);
			MutableString[] test = new MutableString[n];
			final int step = (int)(size / n) - 1;
			final Iterator<? extends CharSequence> iterator = flc.iterator();
			for(int i = 0; i < n; i++) {
				test[i] = new MutableString(iterator.next());
				for(int j = step; j-- != 0;) iterator.next();
			}
			if (shuffle) Collections.shuffle(Arrays.asList(test));

			if (save != null) {
				final PrintStream ps = new PrintStream(save, encoding.name());
				for(final MutableString s: test) s.println(ps);
				ps.close();
			}

			final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			for(final MutableString s: test) s.writeSelfDelimUTF8(fbaos);
			fbaos.close();
			test = null;
			final FastByteArrayInputStream fbais = new FastByteArrayInputStream(fbaos.array, 0, fbaos.length);
			final MutableString s = new MutableString();

			System.gc();
			System.gc();

			long t = -1;
			final long[] sample = new long[NUM_SAMPLES];
			System.err.println("Warmup...");
			for(int k = 15; k-- != 0;) {
				fbais.position(0);
				long time = -System.nanoTime();
				for(int i = 0; i < n; i++) {
					t ^= function.getLong(s.readSelfDelimUTF8(fbais));
					if ((i & 0xFFFFF) == 0) System.err.print('.');
				}
				System.err.println();
				time += System.nanoTime();
				if (k < NUM_SAMPLES) sample[k] = time;
				System.err.println(Util.format(time / 1E9) + "s, " + Util.format((double)time / n) + " ns/item");
				if (k == NUM_SAMPLES) System.err.println("Sampling " + n + " strings...");
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
			for(int k = 15; k-- != 0;) {
				final Iterator<? extends CharSequence> iterator = flc.iterator();

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
				if (k == NUM_SAMPLES) System.err.println("Sampling " + size + " strings...");
			}
			System.out.println("Median: " + Util.format(sample[NUM_SAMPLES / 2] / 1E9) + "s, " + Util.format(sample[NUM_SAMPLES / 2] / (double)size) + " ns/item");
			if (t == 0) System.err.println(t);
		}
	}
}
