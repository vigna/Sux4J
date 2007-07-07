package it.unimi.dsi.sux4j.bits;

/*		 
 * MG4J: Managing Gigabytes for Java
 *
 * Copyright (C) 2005-2007 Sebastiano Vigna 
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

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;

/** A boolean-list based implementation of {@link it.unimi.dsi.sux4j.bits.BitVector}.
 * 
 * <P>This implementation of a bit vector is based on a backing
 * list of booleans. It is rather inefficient, but useful for
 * wrapping purposes, and for procucing mock objects.
 */
public class BooleanListBitVector extends AbstractBitVector {
	/** The backing list. */
	final private BooleanList list;
	
	protected BooleanListBitVector( final BooleanList list ) { this.list = list; }
	
	public BooleanListBitVector() {
		this( new BooleanArrayList( 0 ) );
	}
	
	public static BooleanListBitVector wrap( final BooleanList list ) {
		return new BooleanListBitVector( list );
	}
	
	public int size() {
		return list.size();
	}
	
	public boolean set( final int index, final boolean value ) {
		return list.set( index, value );
	}
	
	public boolean getBoolean( final int index ) {
		return list.getBoolean( index );
	}
	
	public void add( final int index, final boolean value ) {
		list.add( index, value );
	}

	public boolean removeBoolean( final int index ) {
		return list.removeBoolean( index );
	}
	
	public BitVector copy( final int from, final int to ) {
		return new BooleanListBitVector( list.subList( from, to ) );
	}
	
	public BitVector copy() {
		return new BooleanListBitVector( new BooleanArrayList( list ) );
	}
}
