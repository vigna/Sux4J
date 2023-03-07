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

import static org.junit.Assert.assertEquals;

import it.unimi.dsi.bits.BitVector;

public abstract class RankSelectTestCase {
	public void assertRankAndSelect(final Rank rank, final Select select) {
		final long length = rank.bitVector().length();
		final BitVector bits = rank.bitVector();

		for(int j = 0, i = 0; i < length; i++) {
			assertEquals("Ranking " + i, j, rank.rank(i));
			if (bits.getBoolean(i)) {
				assertEquals("Selecting " + j, i, select.select(j));
				j++;
			}

		}
	}

	public void assertSelect(final Select s) {
		final BitVector bits = s.bitVector();
		final long length = bits.length();

		for(int j = 0, i = 0; i < length; i++) {
			if (bits.getBoolean(i)) {
				assertEquals("Selecting " + j, i, s.select(j));
				j++;
			}

		}
	}

	public void assertSelectZero(final SelectZero s) {
		final BitVector bits = s.bitVector();
		final long length = bits.length();

		for(int j = 0, i = 0; i < length; i++) {
			if (! bits.getBoolean(i)) {
				assertEquals("Selecting " + j, i, s.selectZero(j));
				j++;
			}

		}
	}

	public void assertRank(final Rank rank) {
		final long length = rank.bitVector().length();
		final BitVector bits = rank.bitVector();

		for(long j = 0, i = 0; i < length; i++) {
			assertEquals("Ranking " + i, j, rank.rank(i));
			if (bits.getBoolean(i)) j++;
		}
	}

}
