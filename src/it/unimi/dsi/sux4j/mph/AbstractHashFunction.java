package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2016 Sebastiano Vigna 
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

import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;

/** A very minimal abstract hash implementation. It extends {@link AbstractObject2LongFunction},
 * by {@link Size64}. Moreover, it provides a deprecated <code>size()</code> method that returns
 * -1 if {@link #size64()} is -1 or greater than {@link Integer#MAX_VALUE}, a {@link #size64()} returning -1 (that
 * you are invited to override), and a {@link #containsKey(Object)} implementation that returns true.
 */

public abstract class AbstractHashFunction<K> extends AbstractObject2LongFunction<K> implements Size64 {
	private static final long serialVersionUID = 2L;

	public boolean containsKey( Object key ) {
		return true;
	}

	@Deprecated
	public int size() {
		final long size64 = size64();
		return size64 > Integer.MAX_VALUE ? -1 : (int)size64; 
	}
	
	public long size64() {
		return -1;
	}
}
