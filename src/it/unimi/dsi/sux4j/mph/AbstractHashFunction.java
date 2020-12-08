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

package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;

/** A very minimal abstract hash implementation. It extends {@link AbstractObject2LongFunction},
 * by {@link Size64}. Moreover, it provides a deprecated <code>size()</code> method that returns
 * -1 if {@link #size64()} is -1 or greater than {@link Integer#MAX_VALUE}, a {@link #size64()} returning -1 (that
 * you are invited to override), and a {@link #containsKey(Object)} implementation that returns true.
 */

public abstract class AbstractHashFunction<K> extends AbstractObject2LongFunction<K> implements Size64 {
	private static final long serialVersionUID = 2L;

	@Override
	public boolean containsKey(final Object key) {
		return true;
	}

	@Override
	@Deprecated
	public int size() {
		final long size64 = size64();
		return size64 > Integer.MAX_VALUE ? -1 : (int)size64;
	}

	@Override
	public long size64() {
		return -1;
	}
}
