package it.unimi.dsi.sux4j.mph;

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

import it.unimi.dsi.sux4j.bits.BitVector;

public class Hashes {

	private Hashes() {}
	
	public static void jenkins( final BitVector bv, final long init, final long[] h )  {
		final long length = bv.length();
		long a, b, c, from = 0;

		/* Set up the internal state */
		a = b = init;
		c = 0x9e3779b97f4a7c13L; /* the golden ratio; an arbitrary value */

		while ( length - from >= Long.SIZE * 3 ) {
			a += bv.getLong( from, from + Long.SIZE );
			b += bv.getLong( from + Long.SIZE, from + 2 * Long.SIZE );
			c += bv.getLong( from + 2 * Long.SIZE, from + 3 * Long.SIZE );

			a -= b; a -= c; a ^= (c >>> 43);
			b -= c; b -= a; b ^= (a << 9);
			c -= a; c -= b; c ^= (b >>> 8);
			a -= b; a -= c; a ^= (c >>> 38);
			b -= c; b -= a; b ^= (a << 23);
			c -= a; c -= b; c ^= (b >>> 5);
			a -= b; a -= c; a ^= (c >>> 35);
			b -= c; b -= a; b ^= (a << 49);
			c -= a; c -= b; c ^= (b >>> 11);
			a -= b; a -= c; a ^= (c >>> 12);
			b -= c; b -= a; b ^= (a << 18);
			c -= a; c -= b; c ^= (b >>> 22);

			from += 3 * Long.SIZE;
		}

		c += length << 3;

		switch( (int)( ( length - from ) / Long.SIZE ) )  {/* all the case statements fall through */
		case  2: b += bv.getLong( from + Long.SIZE, from + 2 * Long.SIZE );
		case  1: a += bv.getLong( from, from + Long.SIZE );
		/* case 0: nothing left to add */
		}
		
		final long residual = length % Long.SIZE;
		if ( residual != 0 ) c += bv.getLong( length - residual, length );

		a -= b; a -= c; a ^= (c >>> 43);
		b -= c; b -= a; b ^= (a << 9);
		c -= a; c -= b; c ^= (b >>> 8);
		a -= b; a -= c; a ^= (c >>> 38);
		b -= c; b -= a; b ^= (a << 23);
		c -= a; c -= b; c ^= (b >>> 5);
		a -= b; a -= c; a ^= (c >>> 35);
		b -= c; b -= a; b ^= (a << 49);
		c -= a; c -= b; c ^= (b >>> 11);
		a -= b; a -= c; a ^= (c >>> 12);
		b -= c; b -= a; b ^= (a << 18);
		c -= a; c -= b; c ^= (b >>> 22);
		
		h[ 0 ] = a;
		h[ 1 ] = b;
		h[ 2 ] = c;
	}
}
