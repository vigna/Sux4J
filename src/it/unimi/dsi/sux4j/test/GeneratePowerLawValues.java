package it.unimi.dsi.sux4j.test;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.math3.distribution.ZipfDistribution;
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

public class GeneratePowerLawValues {
	public static final Logger LOGGER = LoggerFactory.getLogger(GeneratePowerLawValues.class);

	public static void main(final String[] arg) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP(GeneratePowerLawValues.class.getName(), "Generates a binary list of power-law distributed longs starting from zero.",
				new Parameter[] {
					new UnflaggedOption("gamma", JSAP.DOUBLE_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The power law exponent."),
					new UnflaggedOption("max", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The strict upper bound for the support of the distribution."),
					new UnflaggedOption("n", JSAP.LONG_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of longs."),
					new UnflaggedOption("output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final double gamma = jsapResult.getDouble("gamma");
		final int max = jsapResult.getInt("max");
		final long n = jsapResult.getLong("n");
		final String output = jsapResult.getString("output");

		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.expectedUpdates = n;
		pl.start("Generating... ");

		final ZipfDistribution zipf = new ZipfDistribution(r, max, gamma);
		final DataOutputStream dos = new DataOutputStream(new FastBufferedOutputStream(new FileOutputStream(output)));

		for(long i = 0; i < n; i++) dos.writeLong(zipf.sample() - 1);

		pl.done();
		dos.close();
	}
}
