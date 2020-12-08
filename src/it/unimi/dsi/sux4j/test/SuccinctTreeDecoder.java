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

import java.io.IOException;
import java.io.Serializable;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.compression.Decoder;
import it.unimi.dsi.compression.TreeDecoder;
import it.unimi.dsi.fastutil.booleans.BooleanIterator;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.sux4j.bits.JacobsonBalancedParentheses;

public class SuccinctTreeDecoder implements Decoder, Serializable {
	private static final long serialVersionUID = 1L;

	private final JacobsonBalancedParentheses balParen;
	private final LongArrayBitVector bitVector;
	private final boolean returnZero;

	public SuccinctTreeDecoder(final TreeDecoder treeDecoder) {
		bitVector = treeDecoder.succinctRepresentation();
		//System.err.println(bitVector);
		//System.err.println(Arrays.toString(treeDecoder.buildCodes()));
		returnZero = bitVector.length() <= 2;
		balParen = new JacobsonBalancedParentheses(bitVector, false, true, false);
	}

	@Override
	public int decode(final BooleanIterator iterator) {
		if (returnZero) return 0;
		int p = 1, index = 0;

		for(;;) {
			if (iterator.nextBoolean()) {
				final int q = (int)(balParen.findClose(p) + 1);
				index += (q - p) / 2;
				if (! bitVector.getBoolean(q)) return index;
				p = q;
			}
			else if (! bitVector.getBoolean(++p)) return index;
		}
	}

	@Override
	public int decode(final InputBitStream ibs) throws IOException {
		if (returnZero) return 0;
		int p = 1, index = 0;

		for(;;) {
			if (ibs.readBit() != 0) {
				final int q = (int)(balParen.findClose(p) + 1);
				index += (q - p) / 2;
				if (! bitVector.getBoolean(q)) return index;
				p = q;
			}
			else if (! bitVector.getBoolean(++p)) return index;
		}
	}
}
