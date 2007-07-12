package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2007 Sebastiano Vigna 
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

import static it.unimi.dsi.mg4j.util.Fast.mostSignificantBit;

/** All-purpose optimised static-method container class.
 *
 * <P>This class contains static optimised utility methods that are used by all
 * Sux4J classes.
 *
 * @author Sebastiano Vigna
 * @since 0.1
 */

public final class Fast {
	private Fast() {}
	
	public static int ceilLog2( final int x ) {
		return mostSignificantBit( x - 1 ) + 1;
	}

	public static int ceilLog2( final long x ) {
		return mostSignificantBit( x - 1 ) + 1;
	}

	public static int length( final int x ) {
		return x == 0 ? 1 : mostSignificantBit( x ) + 1;
	}

	public static int length( final long x ) {
		return x == 0 ? 1 : mostSignificantBit( x ) + 1;
	}
}
