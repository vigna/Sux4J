package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import it.unimi.dsi.fastutil.objects.Object2LongFunction;

import java.io.Serializable;
import java.util.List;

/** A map from strings to numbers (and possibly <i>vice versa</i>).
 * 
 * <p>String maps represent mappings from strings (actually, any subclass of {@link CharSequence})
 * to numbers; they can support {@linkplain #list() reverse
 * mapping}, too. The latter has usually sense only if the map is minimal and perfect (e.g., a bijection of a set
 * of string with an initial segment of the natural numbers of the same size). String maps are useful for
 * terms of an <a href="http://mg4j.dsi.unimi.it/">MG4J</a>
 * inverted index, URLs of a <a href="http://webgraph.dsi.unimi.it/">WebGraph</a>-compressed
 * web snapshot, and so on.
 * 
 * <p><strong>Warning</strong>: the return value of {@link #list()} is a standard Java {@link List}.
 * This in principle is not sensible, as string maps return longs (they extend
 * {@link Object2LongFunction}), and {@link List} has only integer index
 * support. At some point in the future, this problem will be addressed with a full-blown hierarchy
 * of long lists (and corresponding iterators)
 * in <code><a href="http://fastutil.dsi.unimi.it/">fastutil</a></code>.
 *  
 * @author Sebastiano Vigna 
 * @since 0.2
 */

public interface StringMap<S extends CharSequence> extends Object2LongFunction<S>, Serializable {
	
	/** Returns a list view of the domain of this string map (optional operation). 
	 * 
	 * <p>Note that the list view acts as an inverse of the mapping implemented by this map.
	 * 
	 * @return a list view of the domain of this string map, or <code>null</code> if this map does
	 * not support this operation.
	 */
	
	public List<S> list();
}
