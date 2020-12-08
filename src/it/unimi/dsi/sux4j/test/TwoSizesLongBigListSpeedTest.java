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

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.scratch.EliasFanoMonotoneLongBigListTables;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.sux4j.util.EliasFanoPrefixSumLongBigList;
import it.unimi.dsi.sux4j.util.TwoSizesLongBigList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class TwoSizesLongBigListSpeedTest {

	public static void main(final String[] arg) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(TwoSizesLongBigListSpeedTest.class.getName(), "Tests the speed of rank/select implementations.",
				new Parameter[] {
					new UnflaggedOption("numElements", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of elements."),
					new UnflaggedOption("density", JSAP.DOUBLE_PARSER, ".5", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The density."),
					new FlaggedOption("numPos", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, 'p', "positions", "The number of positions to test"),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final int numElements = jsapResult.getInt("numElements");
		final double density = jsapResult.getDouble("density");
		final int numPos = jsapResult.getInt("numPos");

		final XoRoShiRo128PlusRandomGenerator random = new XoRoShiRo128PlusRandomGenerator(0);
		final LongArrayList list = new LongArrayList(numElements);
		for(long i = numElements; i-- != 0;) list.add(random.nextDouble() < density ? 0 : 100);

		final int[] position = new int[numPos];

		for(int i = numPos; i-- != 0;) position[i] = (random.nextInt() & 0x7FFFFFFF) % numElements;
		final TwoSizesLongBigList twoSizes = new TwoSizesLongBigList(list);
		final EliasFanoLongBigList eliasFano = new EliasFanoLongBigList(list);
		final EliasFanoPrefixSumLongBigList eliasFanoPrefixSum = new EliasFanoPrefixSumLongBigList(list);
		final long[] elements = list.elements();
		for(int i = 1; i < list.size(); i++) elements[i] += elements[i - 1];
		final EliasFanoMonotoneLongBigList monotone = new EliasFanoMonotoneLongBigList(list);
		final EliasFanoMonotoneLongBigListTables tables = new EliasFanoMonotoneLongBigListTables(list);

		long time;

		for(int k = 10; k-- != 0;) {
			System.out.println("=== LongArrayList === (" + list.size() * (long)Long.SIZE + " bits)");
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) list.getLong(position[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + (double)time / numPos + " ns/get");

			System.out.println("=== TwoSizesLongBigList === (" + twoSizes.numBits() + " bits)");
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) twoSizes.getLong(position[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + (double)time / numPos + " ns/get");

			System.out.println("=== EliasFanoPrefixSumLongBigList === (" + eliasFanoPrefixSum.numBits() + " bits)");
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) eliasFanoPrefixSum.getLong(position[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + (double)time / numPos + " ns/get");

			System.out.println("=== EliasFanoLongBigList === (" + eliasFano.numBits() + " bits)");
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) eliasFano.getLong(position[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + (double)time / numPos + " ns/get");

			System.out.println("=== EliasFanoMonotoneLongBigListTables === (" + tables.numBits() + " bits)");
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) tables.getLong(position[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + (double)time / numPos + " ns/get");

			System.out.println("=== EliasFanoMonotoneLongBigList === (" + monotone.numBits() + " bits)");
			time = - System.nanoTime();
			for(int i = 0; i < numPos; i++) monotone.getLong(position[i]);
			time += System.nanoTime();
			System.err.println(time / 1E9 + "s, " + (double)time / numPos + " ns/get");
		}
	}
}
