package it.unimi.dsi.sux4j.test;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.apache.commons.math3.random.RandomGenerator;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class GenerateRandomStrings {

	public static void main(final String[] arg) throws JSAPException, UnsupportedEncodingException, FileNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP(GenerateRandomStrings.class.getName(), "Generates random strings",
				new Parameter[] {
					new UnflaggedOption("n", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of strings."),
					new UnflaggedOption("l", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of characters per string."),
					new UnflaggedOption("output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final int n = jsapResult.getInt("n");
		final int l = jsapResult.getInt("l");
		final String output = jsapResult.getString("output");

		final MutableString[] s = new MutableString[n];
		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();

		int p;

		do {
			for(int i = 0; i < n; i++) {
				final MutableString t = new MutableString(l);
				for(int j = 0; j < l; j++) t.append((char)r.nextInt(255) + 1);
				s[i] = t;
			}

			Arrays.sort(s);

			for(p = 1; p < n; p++) if (s[p].equals(s[p - 1])) break;
		} while(p < n);

		final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), "ISO-8859-1"));
		for(final MutableString t : s) t.println(pw);
		pw.close();
	}
}
