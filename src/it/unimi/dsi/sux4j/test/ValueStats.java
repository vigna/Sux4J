/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2021 Sebastiano Vigna
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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

public class ValueStats {
	public static final Logger LOGGER = LoggerFactory.getLogger(ValueStats.class);

	public static void main(final String[] arg) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP(ValueStats.class.getName(), "Prints statistical data about a binary list of longs.",
				new Parameter[] {
					new UnflaggedOption("input", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The input file.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String input = jsapResult.getString("input");
		long max = Long.MIN_VALUE;
		long min = Long.MAX_VALUE;
		long tot = 0;
		final Long2LongOpenHashMap freqs = new Long2LongOpenHashMap();

		for(final LongIterator i = BinIO.asLongIterator(input); i.hasNext(); ) {
			final long x = i.nextLong();
			max = Math.max(max, x);
			min = Math.min(min, x);
			freqs.addTo(x, 1);
			tot++;
		}

		System.out.println("Min: " + min);
		System.out.println("Max: " + max);
		double entropy = 0;
		for(final LongIterator iterator = freqs.values().iterator(); iterator.hasNext();) {
			final double p = (double)iterator.nextLong() / tot;
			entropy += -p * Fast.log2(p);
		}
		System.out.println("Entropy: " + entropy);
	}
}
