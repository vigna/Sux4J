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


import org.apache.commons.math3.random.RandomGenerator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank11;
import it.unimi.dsi.sux4j.bits.Rank12;
import it.unimi.dsi.sux4j.bits.Rank16;
import it.unimi.dsi.sux4j.bits.Rank9;
import it.unimi.dsi.sux4j.scratch.Rank11Original;
import it.unimi.dsi.sux4j.scratch.Rank9GogPetri;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class RankSpeedTest {

	public static void main(final String[] arg) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(RankSpeedTest.class.getName(), "Tests the speed of rank/select implementations.",
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
		for(long i = numBits; i-- != 0;) if (random.nextDouble() < density) bitVector.set(i);

		final long[] rankPosition = new long[numPos];

		for(int i = numPos; i-- != 0;) rankPosition[i] = (random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % numBits;

		long time;
		for(int k = 10; k-- != 0;) {
			System.out.println("=== Rank 9 ===");
			final Rank9 rank9 = new Rank9(bitVector);
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) rank9.rank(rankPosition[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + time / (double)numPos + " ns/rank (" + 100.0 * rank9.numBits() / numBits + "%)");

			System.out.println("=== Rank 9b ===");
			final Rank9GogPetri rank9b = new Rank9GogPetri(bitVector);
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) rank9b.rank(rankPosition[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + time / (double)numPos + " ns/rank (" + 100.0 * rank9b.numBits() / numBits + "%)");

			System.out.println("=== Rank 16 ===");
			final Rank16 rank16 = new Rank16(bitVector);
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) rank16.rank(rankPosition[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + time / (double)numPos + " ns/rank (" + 100.0 * rank16.numBits() / numBits + "%)");

			System.out.println("=== Rank 11 ===");
			final Rank11Original rank11 = new Rank11Original(bitVector);
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) rank11.rank(rankPosition[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + time / (double)numPos + " ns/rank (" + 100.0 * rank11.numBits() / numBits + "%)");

			System.out.println("=== Rank 11b ===");
			final Rank11 rank11b = new Rank11(bitVector);
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) rank11b.rank(rankPosition[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + time / (double)numPos + " ns/rank (" + 100.0 * rank11b.numBits() / numBits + "%)");

			System.out.println("=== Rank 12 ===");
			final Rank12 rank12 = new Rank12(bitVector);
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) rank12.rank(rankPosition[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + time / (double)numPos + " ns/rank (" + 100.0 * rank12.numBits() / numBits + "%)");
		}
	}
}
