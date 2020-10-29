/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2020 Sebastiano Vigna
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
