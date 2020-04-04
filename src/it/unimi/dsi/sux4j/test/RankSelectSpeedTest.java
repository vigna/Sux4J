package it.unimi.dsi.sux4j.test;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2020 Sebastiano Vigna
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
import it.unimi.dsi.sux4j.bits.HintedBsearchSelect;
import it.unimi.dsi.sux4j.bits.Rank16;
import it.unimi.dsi.sux4j.bits.Rank9;
import it.unimi.dsi.sux4j.bits.Select9;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.sux4j.bits.SparseSelect;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class RankSelectSpeedTest {

	public static void main(final String[] arg) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(RankSelectSpeedTest.class.getName(), "Tests the speed of rank/select implementations.",
				new Parameter[] {
					new UnflaggedOption("numBits", JSAP.LONGSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of bits."),
					new UnflaggedOption("density", JSAP.DOUBLE_PARSER, ".5", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The density."),
					new FlaggedOption("numPos", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, 'p', "positions", "The number of positions to test"),
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
		for(int k = 10; k-- != 0;) {
			System.out.println("=== Rank 9 ===");
			final Rank9 rank9 = new Rank9(bitVector);
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) rank9.rank(rankPosition[i]);
			time += System.currentTimeMillis();
			System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/rank");

			System.out.println("=== Rank 16 ===");
			final Rank16 rank16 = new Rank16(bitVector);
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) rank16.rank(rankPosition[i]);
			time += System.currentTimeMillis();
			System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/rank");

			System.out.println("=== Hinted bsearch ===");
			final HintedBsearchSelect hintedBsearchSelect = new HintedBsearchSelect(rank9);
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) hintedBsearchSelect.select(selectPosition[i]);
			time += System.currentTimeMillis();
			System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/select");

			System.out.println("=== Select9 ===");
			final Select9 select9 = new Select9(rank9);
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) select9.select(selectPosition[i]);
			time += System.currentTimeMillis();
			System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/select");

			System.out.println("=== Simple ===");
			final SimpleSelect simpleSelect = new SimpleSelect(bitVector);
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) simpleSelect.select(selectPosition[i]);
			time += System.currentTimeMillis();
			System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/select");

			System.out.println("=== Sparse ===");
			final SparseSelect sparseSelect = new SparseSelect(bitVector);
			time = - System.currentTimeMillis();
			for(int i = 0; i < numPos; i++) sparseSelect.select(selectPosition[i]);
			time += System.currentTimeMillis();
			System.err.println(time / 1000.0 + "s, " + (time * 1E6) / numPos + " ns/select");
		}
	}
}
