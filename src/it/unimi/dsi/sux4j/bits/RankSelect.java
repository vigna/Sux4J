/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
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

import java.io.Serializable;

import it.unimi.dsi.bits.BitVector;

/** A serialisation-oriented container for associated rank/select(zero) structures.
 *
 *  <p>Since structures in Sux4J serialise all contained data, including, if necessary, the underlying bit vector,
 *  serialising separately a rank and a select structure might result in storing the underlying bit
 *  vector twice. This class provide a simple solution by allowing one-shot serialisation of
 *  all structures related to a bit vector. For convenience, it provides also delegate methods, albeit
 *  the suggested usage is deserialisation and extraction of non-{@code null} structures.
 *
 */
public class RankSelect implements Rank, Select, SelectZero, Serializable {

	private static final long serialVersionUID = 1L;
	/** A rank structure, or {@code null}. */
	public final Rank rank;
	/** A select structure, or {@code null}. */
	public final Select select;
	/** A zero-select structure, or {@code null}. */
	public final SelectZero selectZero;

	/** Creates a new rank/select container using the given structures.
	 *
	 * @param rank a rank structure, or {@code null}.
	 * @param select a select structure, or {@code null}.
	 * @param selectZero a zero-select structure, or {@code null}.
	 */
	public RankSelect(final Rank rank, final Select select, final SelectZero selectZero) {
		this.rank = rank;
		this.select = select;
		this.selectZero = selectZero;
	}

	/** Creates a new rank/select container without zero selection using the given structures.
	 *
	 * @param rank a rank structure, or {@code null}.
	 * @param select a select structure, or {@code null}.
	 */
	public RankSelect(final Rank rank, final Select select) {
		this(rank, select, null);
	}

	@Override
	public long count() {
		return rank.count();
	}

	@Override
	public long numBits() {
		return (rank != null ? rank.numBits() : 0) + (select != null ? select.numBits() : 0)+ (selectZero != null ? selectZero.numBits() : 0);
	}

	@Override
	public long rank(final long from, final long to) {
		return rank.rank(from, to);
	}

	@Override
	public long rank(final long pos) {
		return rank.rank(pos);
	}

	@Override
	public long rankZero(final long from, final long to) {
		return rank.rankZero(from, to);
	}

	@Override
	public long rankZero(final long pos) {
		return rank.rankZero(pos);
	}

	@Override
	public long select(final long rank) {
		return select.select(rank);
	}

	@Override
	public long selectZero(final long rank) {
		return selectZero.selectZero(rank);
	}

	@Override
	public BitVector bitVector() {
		if (rank != null) return rank.bitVector();
		if (select != null) return select.bitVector();
		if (selectZero != null) return selectZero.bitVector();
		throw new UnsupportedOperationException("All fields are nulls");
	}

}
