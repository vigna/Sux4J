/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2023 Sebastiano Vigna
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

import org.apache.commons.math3.random.RandomGenerator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigArrays;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneBigLongBigList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class EliasFanoMonotoneBigLongBigListSpeedTest {

	public static void main(final String[] arg) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(EliasFanoMonotoneBigLongBigListSpeedTest.class.getName(), "Tests the speed Elias-Fano monotone lists.",
				new Parameter[] {
						new UnflaggedOption("n", JSAP.LONGSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of elements."),
						new UnflaggedOption("u", JSAP.LONGSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The universe size."),
					new FlaggedOption("numPos", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, 'p', "positions", "The number of positions to test"),
					new FlaggedOption("bulk", JSAP.INTSIZE_PARSER, "10", JSAP.NOT_REQUIRED, 'b', "bulk", "The number of positions to read with the bulk method"),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final long n = jsapResult.getLong("n");
		final long u = jsapResult.getLong("u");
		final int numPos = jsapResult.getInt("numPos");
		final int bulk = jsapResult.getInt("bulk");

		final RandomGenerator random = new XoRoShiRo128PlusRandomGenerator(42);
		final LongBigArrayBigList elements = new LongBigArrayBigList(n);
		for (long i = n; i-- != 0;) elements.add((random.nextLong() >>> 1) % u);
		LongBigArrays.parallelQuickSort(elements.elements(), 0, n);

		final long[] position = new long[numPos];

		for (int i = numPos; i-- != 0;) position[i] = (random.nextLong() >>> 1) % (n - bulk);
		final EliasFanoMonotoneBigLongBigList eliasFanoMonotoneBigLongBigList = new EliasFanoMonotoneBigLongBigList(elements);
		long time;
		long t = 0;

		final long[] dest = new long[bulk];
		for(int k = 10; k-- != 0;) {
			System.out.print("getLong(): ");
			time = - System.nanoTime();
			for (int i = 0; i < numPos; i++) t += eliasFanoMonotoneBigLongBigList.getLong(position[i]);
			time += System.nanoTime();
			System.out.println(time / 1E9 + "s, " + time / (double)numPos + " ns/element");

			System.out.print("getDelta(): ");
			time = -System.nanoTime();
			for (int i = 0; i < numPos; i++) t += eliasFanoMonotoneBigLongBigList.getDelta(position[i]);
			time += System.nanoTime();
			System.out.println(time / 1E9 + "s, " + time / (double)numPos + " ns/element");

			System.out.print("get(): ");
			time = - System.nanoTime();
			for (int i = 0; i < numPos; i++) {
				eliasFanoMonotoneBigLongBigList.get(position[i], dest);
				t += dest[0];
			}
			time += System.nanoTime();
			System.out.println(time / 1E9 + "s, " + time / (double)(numPos * bulk) + " ns/element");
		}

		if (t == 0) System.out.println();
	}
}
