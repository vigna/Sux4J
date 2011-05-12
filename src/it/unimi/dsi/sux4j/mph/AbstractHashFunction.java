package it.unimi.dsi.sux4j.mph;


/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2011 Sebastiano Vigna 
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

import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;

/** A very minimal abstract hash implementation. With respect to {@link AbstractObject2LongFunction},
 * it simply returns -1 for {@link #size()} and true for {@link #containsKey(Object)}.
 */

public abstract class AbstractHashFunction<K> extends AbstractObject2LongFunction<K> {
	public static final long serialVersionUID = 1L;
	public boolean containsKey( Object key ) {
		return true;
	}

	public int size() {
		return -1;
	}
}
