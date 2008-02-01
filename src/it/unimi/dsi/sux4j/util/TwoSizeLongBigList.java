package it.unimi.dsi.sux4j.util;

import cern.colt.Sorting;
import cern.colt.function.IntComparator;
import cern.colt.function.LongComparator;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.sux4j.bits.LongBigList;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2007-2008 Sebastiano Vigna 
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

public class TwoSizeLongBigList extends AbstractLiimplements LongBigList {

	public TwoSizeLongBigList( final LongBigList list ) {
		final Long2LongOpenHashMap counts = new Long2LongOpenHashMap();
		for( long x: list ) counts.put( x, counts.get( x ) + 1 );
		final long[] keys = counts.keySet().toLongArray();
		Sorting.quickSort( keys, 0, keys.length, new LongComparator() {

			public int compare( long o1, long o2 ) {
				return (int)( counts.get( o1 ) - counts.get( o2 ) );
			}
		});
	}
}
