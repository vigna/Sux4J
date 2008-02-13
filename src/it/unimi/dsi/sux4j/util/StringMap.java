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
 * <p>String maps represent mappings from string (actually, any subclass of {@link CharSequence})
 * to numbers, and can support {@linkplain #list() reverse
 * mapping}, too. The latter has usually sense only if the map is minimal and perfect (e.g., a bijection of a set
 * of string with an initial segment of the natural numbers of the same size).
 * 
 * @author Sebastiano Vigna 
 * @since 0.2
 */

public interface StringMap<S extends CharSequence> extends Object2LongFunction<S>, Serializable {
	
	/** Returns a list view of the domain of this string map (optional operation). 
	 * 
	 * @return a list view of the domain of this string map, or <code>null</code> if this map does
	 * not support it.
	 */
	
	public List<S> list();
}
