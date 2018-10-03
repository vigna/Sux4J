package it.unimi.dsi.sux4j.test;

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

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP(FunctionSpeedTest.class.getName(), "Test the speed of a function. Performs thirteen repetitions: the first three ones are warmup, and the average of the remaining ten is printed on standard output. The detailed results are logged to standard error.",
				new Parameter[] {
					new FlaggedOption("n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-strings", "The (maximum) number of strings used for random testing."),
					new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding."),
					new FlaggedOption("save", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "save", "In case of a random test, save to this file the strings used."),
					new Switch("zipped", 'z', "zipped", "The term list is compressed in gzip format."),
					new Switch("random", 'r', "random", "Test a shuffled subset of strings."),
					new Switch("check", 'c', "check", "Check that the term list is mapped to its ordinal position."),
					new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised function."),
					new UnflaggedOption("termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Read terms from this file."),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String termFile = jsapResult.getString("termFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean check = jsapResult.getBoolean("check");
		final boolean random = jsapResult.getBoolean("random");
		final String save = jsapResult.getString("save");
		final int maxStrings = jsapResult.getInt("n");

		if (jsapResult.userSpecified("n") && ! random) throw new IllegalArgumentException("The number of string is meaningful for random tests only");
		if (save != null && ! random) throw new IllegalArgumentException("You can save test string only for random tests");

		@SuppressWarnings("unchecked")
		final Object2LongFunction<? extends CharSequence> function = (Object2LongFunction<? extends CharSequence>)BinIO.loadObject(functionName);
		final FileLinesCollection flc = new FileLinesCollection(termFile, encoding.name(), zipped);
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
			Collections.shuffle(Arrays.asList(test));

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

			long total = 0, t = -1;
			for(int k = 13; k-- != 0;) {
				fbais.position(0);
				long time = -System.nanoTime();
				for(int i = 0; i < n; i++) {
					t ^= function.getLong(s.readSelfDelimUTF8(fbais));
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
				if (k < 10) total += time;
				System.err.println(Util.format(time / 1E9) + "s, " + Util.format((double)time / size) + " ns/item");
			}
			System.out.println("Average: " + Util.format(total / 1E10) + "s, " + Util.format(total / (10. * size)) + " ns/item");
			if (t == 0) System.err.println(t);
		}
	}
}
