/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2007-2021 Sebastiano Vigna
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

/** An abstract implementation of {@link Rank} providing a few obvious derived methods. */

public abstract class AbstractRank implements Rank {
	private static final long serialVersionUID = 1L;

	@Override
	public long count() {
		return rank(bitVector().length());
	}

	@Override
	public long rank(final long from, final long to) {
		return rank(to) - rank(from);
	}

	@Override
	public long rankZero(final long pos) {
		return pos - rank(pos);
	}

	@Override
	public long rankZero(final long from, final long to) {
		return to - from - rank(from, to);
	}
}
