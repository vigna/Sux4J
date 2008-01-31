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

public class Hashes {

	private Hashes() {}
	
	/** Computes Paul Hsieh's SuperFastHash.
	 * 
	 * @param data an array of characters.
	 * @param init an initialisation value for the hash.
	 * @param len the number of characters to digest.
	 * @return the hash.
	 */
	
    public static int superFastHash( char[] data, int init, int len ) {
		int hash = init, tmp;

		for ( int i = 0; i < len - 1; i += 2 ) {
			hash += data[ i ];
			tmp = ( data[ i + 1 ] << 11 ) ^ hash;
			hash = ( hash << 16 ) ^ tmp;
			hash += hash >>> 11;
		}

		if ( ( len & 1 ) != 0 ) {
			hash += data[ len - 1 ];
			hash ^= hash << 11;
			hash += hash >>> 17;
		}

		hash ^= hash << 3;
		hash += hash >>> 5;
		hash ^= hash << 4;
		hash += hash >>> 17;
		hash ^= hash << 25;
		hash += hash >>> 6;

		return hash;
	}

}
