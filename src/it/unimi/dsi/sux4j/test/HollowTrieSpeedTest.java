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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;

public class HollowTrieSpeedTest {

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP(HollowTrieSpeedTest.class.getName(), "Tests the speed of a hollow trie.",
				new Parameter[] {
					new FlaggedOption("bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read terms."),
					new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding."),
					new Switch("zipped", 'z', "zipped", "The term list is compressed in gzip format."),
					new FlaggedOption("termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read terms from this file (without loading them into core memory) instead of standard input."),
					new UnflaggedOption("trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie.")
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final int bufferSize = jsapResult.getInt("bufferSize");
		final String trieName = jsapResult.getString("trie");
		final String termFile = jsapResult.getString("termFile");
		//final Class<?> tableClass = jsapResult.getClass("class");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final boolean zipped = jsapResult.getBoolean("zipped");

		@SuppressWarnings("unchecked")
		final Object2LongFunction<? extends CharSequence> hollowTrie = (Object2LongFunction<? extends CharSequence>)BinIO.loadObject(trieName);

		Iterator<? extends CharSequence> i;

		for(int k = 10; k-- != 0;) {
			if (termFile == null) i = new LineIterator(new FastBufferedReader(new InputStreamReader(System.in, encoding), bufferSize));
			else i = new LineIterator(new FastBufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(new FileInputStream(termFile)) : new FileInputStream(termFile), encoding), bufferSize));
			long time = -System.currentTimeMillis();
			int j = 0;
			while(i.hasNext()) {
				hollowTrie.getLong(i.next());
				if (j++ % 10000 == 0) System.err.print('.');
			}
			System.err.println();
			time += System.currentTimeMillis();
			System.err.println(time / 1E3 + "s, " + (time * 1E6) / j + " ns/vector");
		}
	}
}
