/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2023 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;

public class TrivialBalancedParentheses implements BalancedParentheses {
	private static final long serialVersionUID = 1L;
	private final BitVector v;

	public TrivialBalancedParentheses(final BitVector v) {
		this.v = v;
	}

	@Override
	public BitVector bitVector() {
		return v;
	}

	@Override
	public long enclose(final long pos) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long findClose(long pos) {
		if (! v.getBoolean(pos)) throw new IllegalArgumentException();
		int c = 1;
		while(++pos < v.length()) {
			if (! v.getBoolean(pos)) c--;
			else c++;
			if (c == 0) return pos;
		}

		throw new IllegalArgumentException();
	}

	@Override
	public long findOpen(long pos) {
		if (v.getBoolean(pos)) throw new IllegalArgumentException();

		int c = 1;
		while(--pos >= 0) {
			if (! v.getBoolean(pos)) c++;
			else c--;
			if (c == 0) return pos;
		}

		throw new IllegalArgumentException();
	}

	@Override
	public long numBits() {
		return 0;
	}
}
