package it.unimi.dsi.sux4j.test;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class GenerateUniformValues {
	public static final Logger LOGGER = LoggerFactory.getLogger(GenerateUniformValues.class);

	public static void main(final String[] arg) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP(GenerateUniformValues.class.getName(), "Generates a binary list of uniformly distributed longs using a given number of bits.",
				new Parameter[] {
					new UnflaggedOption("b", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of bits."),
					new UnflaggedOption("n", JSAP.LONG_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of longs."),
					new UnflaggedOption("output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final int b = jsapResult.getInt("b");
		final long n = jsapResult.getLong("n");
		final String output = jsapResult.getString("output");

		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();
		final long mask = b == 64 ? -1L: (1L << b) - 1;

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.expectedUpdates = n;
		pl.start("Generating... ");

		final DataOutputStream dos = new DataOutputStream(new FastBufferedOutputStream(new FileOutputStream(output)));

		for(long i = 0; i < n; i++) dos.writeLong(r.nextLong() & mask);
		pl.done();
		dos.close();
	}
}
