/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2022 Sebastiano Vigna
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

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongList;

public class ListSpeedTest {

	public static void main(final String[] arg) throws IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP(ListSpeedTest.class.getName(), "Test the speed of a list",
				new Parameter[] {
					new Switch("random", 'r', "random", "Do a random test on at most 1 million strings."),
					new UnflaggedOption("list", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised list.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String listName = jsapResult.getString("list");

		final LongList list = (LongList)BinIO.loadObject(listName);
		long total = 0;
		final int n = list.size();
		for(int k = 13; k-- != 0;) {
			long time = -System.currentTimeMillis();
			for(int i = 0; i < n; i++) {
				list.getLong(i);
				if (i++ % 100000 == 0) System.out.print('.');
			}
			System.out.println();
			time += System.currentTimeMillis();
			if (k < 10) total += time;
			System.out.println(time / 1E3 + "s, " + (time * 1E3) / n + " \u00b5s/item");
		}
		System.out.println("Average: " + Util.format(total / 10E3) + "s, " + Util.format((total * 1E3) / (10 * n)) + " \u00b5s/item");
	}
}
