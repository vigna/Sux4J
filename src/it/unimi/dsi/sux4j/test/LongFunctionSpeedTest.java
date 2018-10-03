package it.unimi.dsi.sux4j.test;

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

	public static void main(final String[] arg) throws IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP(LongFunctionSpeedTest.class.getName(), "Test the speed of a function on longs. Performs thirteen repetitions: the first three ones are warmup, and the average of the remaining ten is printed on standard output. The detailed results are logged to standard error.",
				new Parameter[] {
					new FlaggedOption("n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-longs", "The (maximum) number of longs used for random testing."),
					new Switch("random", 'r', "random", "Test a shuffled subset of longs."),
					new Switch("check", 'c', "check", "Check that the list of longs is mapped to its ordinal position."),
					new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised function."),
					new UnflaggedOption("termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Read terms from this file."),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String termFile = jsapResult.getString("termFile");
		final boolean check = jsapResult.getBoolean("check");
		final boolean random = jsapResult.getBoolean("random");
		final int maxStrings = jsapResult.getInt("n");

		if (jsapResult.userSpecified("n") && ! random) throw new IllegalArgumentException("The number of string is meaningful for random tests only");

		@SuppressWarnings("unchecked")
		final Object2LongFunction<Long> function = (Object2LongFunction<Long>)BinIO.loadObject(functionName);
		@SuppressWarnings("resource")
		final LongArrayList lines = LongArrayList.wrap(BinIO.loadLongs(termFile));

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
			Collections.shuffle(Arrays.asList(test));

			System.gc();
			System.gc();

			long total = 0, t = -1;
			for(int k = 13; k-- != 0;) {
				long time = -System.nanoTime();
				for(int i = 0; i < n; i++) {
					t ^= function.getLong(Long.valueOf(test[i]));
					if ((i % 0xFFFFF) == 0) System.err.print('.');
				}
				System.err.println();
				time += System.nanoTime();
				if (k < 10) total += time;
				System.err.println(Util.format(time / 1E9) + "s, " + Util.format((double)time / n) + " ns/item");
			}
			System.out.println("Average: " + Util.format(total / 1E10) + "s, " + Util.format(total / (10. * n)) + " ns/item");
			if (t == 0) System.err.println(t);
		}
		else {
			System.gc();
			System.gc();

			long total = 0, t = -1;
			for(int k = 13; k-- != 0;) {
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
				if (k < 10) total += time;
				System.err.println(Util.format(time / 1E9) + "s, " + Util.format((double)time / size) + " ns/item");
			}
			System.out.println("Average: " + Util.format(total / 1E10) + "s, " + Util.format(total / (10. * size)) + " ns/item");
			if (t == 0) System.err.println(t);
		}
	}
}
