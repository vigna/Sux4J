package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2010 Sebastiano Vigna 
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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.util.AbstractLongBigList;
import it.unimi.dsi.util.LongBigList;

import java.io.Serializable;

/** An implementation of Elias&ndash;Fano's representation of monotone sequences; an element occupies a number of bits bounded by two plus the logarithm of the average gap.
 * 
 * <p>Instances of this class represent in a highly compacted form a nondecreasing sequence of natural numbers. Instances
 * are built by providing either an iterator returning the (nondecreasing) sequence, or an {@linkplain Iterable iterable object} that
 * provides such an iterator. In the first case, you must also provide in advance the number of elements that will be returned and an upper bound to their
 * values (see below), and at the end of the construction the iterator will be exhausted. 
 *  
 * <h2>Implementation details</h2>
 * 
 * <p>Given a (nondecreasing) monotone sequence 
 * <var>x</var><sub>0</sub>, <var>x</var><sub>1</sub>,&hellip; , <var>x</var><sub><var>n</var> &minus; 1</sub>
 * of natural numbers smaller than <var>u</var>,
 * the Elias&ndash;Fano representation makes it possible to store it using
 * at most 2 + log(<var>u</var>/<var>n</var>) bits per element, which is very close
 * to the information-theoretical lower bound &#x2248; log <i>e</i> + log(<var>u</var>/<var>n</var>). A typical example
 * is a list of pointer into records of a large file: instead of using, for each pointer, a number of bit sufficient to express the length of
 * the file, the Elias&ndash;Fano representation makes it possible to use, for each pointer, a number of bits roughly equal to
 * the logarithm of the average length of a record. The representation was introduced in Peter Elias, 
 * &ldquo;Efficient storage and retrieval by content and address of static files&rdquo;, <i>J. Assoc. Comput. Mach.</i>, 21(2):246&minus;260, 1974,
 * and also independently by Robert Fano, &ldquo;On the number of bits required to implement an associative memory&rdquo;,
 *  Memorandum 61, Computer Structures Group, Project MAC, MIT, Cambridge, Mass., n.d., 1971. 
 * 
 * <p>The elements of the sequence are recorded by storing separately 
 * the lower <var>s</var> = &lfloor;log(<var>u</var>/<var>n</var>)&rfloor; bits and the remaining upper bits.
 * The lower bits are stored contiguously, whereas the upper bits are stored in an array
 * of <var>n</var> + <var>x</var><sub><var>n</var> &minus; 1</sub> / 2<sup><var>s</var></sup> bits by setting,
 * for each 0 &le; <var>i</var> &lt; <var>n</var>,
 * the bit of index <var>x</var><sub><var>i</var></sub> / 2<sup><var>s</var></sup> + <var>i</var>; the value can then be recovered
 * by selecting the <var>i</var>-th bit of the resulting bit array and subtracting <var>i</var> (note that this will
 * work because the upper bits are nondecreasing).
 * 
 * <p>This implementation uses {@link SimpleSelect} to support selection inside the upper-bits array.
 * 
 * <!--<h2><var>k</var>-monotone sequences</h2>
 * 
 * <p>We say that <var>x</var><sub>0</sub>, <var>x</var><sub>1</sub>,&hellip; , <var>x</var><sub><var>n</var> &minus; 1</sub> is
 * <em><var>k</var>-monotone</em> if <var>x</var><sub><var>i</var></sub> &minus; <var>x</var><sub><var>i</var> &minus; 1</sub> &ge; <var>k</var>.
 * There is a natural bijection between <var>k</var>-monotone sequences of <var>n</var> elements with upper bound <var>u</var>
 * and monotone sequences of <var>n</var> elements with upper bound <var>u</var> &minus; <var>kn</var> that can be used to 
 * further reduce space occupancy.
 * -->
 */

public class EliasFanoMonotoneLongBigList extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 2L;
	
	/** The length of the sequence. */
	protected final long length;
	/** The number of lower bits. */
	protected final int l;
	/** The list of lower bits of each element, stored explicitly. */
	protected final LongArrayBitVector lowerBits;
	/** The select structure used to extract the upper bits. */ 
	protected final SimpleSelect selectUpper;
	
	protected EliasFanoMonotoneLongBigList( final long length, final int l, final LongArrayBitVector lowerBits, final SimpleSelect selectUpper ) {
		this.length = length;
		this.l = l;
		this.lowerBits = lowerBits;
		this.selectUpper = selectUpper;
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 * 
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList( final IntIterable list ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( list.iterator() );
			}
		});
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 * 
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList( final ShortIterable list ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( list.iterator() );
			}
		});
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 * 
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList( final ByteIterable list ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( list.iterator() );
			}
		});
	}
	
	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 * 
	 * @param list an iterable object.
	 */

	public EliasFanoMonotoneLongBigList( final LongIterable list ) {
		this( computeParameters( list.iterator() ), list.iterator() );
	}

	/** Computes the number of elements and the last element returned by the given iterator.
	 * 
	 * @param iterator an iterator.
	 * @return a two-element array of longs containing the number of elements returned by
	 * the iterator and the last returned element, respectively.
	 */
	private static long[] computeParameters( final LongIterator iterator ) {
		long v = -1, c = 0;
		while( iterator.hasNext() ) {
			v = iterator.nextLong();
			c++;
		}
		
		return new long[] { c, v + 1 };
	}
	

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a (strict) upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList( final long n, final long upperBound, final ByteIterator iterator ) {
		this( new long[] { n, upperBound }, LongIterators.wrap( iterator ) );
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a (strict) upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList( final long n, final long upperBound, final ShortIterator iterator ) {
		this( new long[] { n, upperBound }, LongIterators.wrap( iterator ) );
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a (strict) upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList( final long n, final long upperBound, final IntIterator iterator ) {
		this( new long[] { n, upperBound }, LongIterators.wrap( iterator ) );
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a (strict) upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList( final long n, final long upperBound, final LongIterator iterator ) {
		this( new long[] { n, upperBound }, iterator );
	}

	/**  Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is used only internally, to work around the usual problems
	 * caused by the obligation to call <code>this()</code> before anything else.
	 * 
	 * @param a an array containing the number of elements returned by <code>iterator</code> and
	 * a (strict) upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	protected EliasFanoMonotoneLongBigList( long[] a, final LongIterator iterator ) {
		length = a[ 0 ];
		long v = -1;
		final long upperBound = a[ 1 ];
		l = length == 0 ? 0 : Math.max( 0, Fast.mostSignificantBit( upperBound / length ) );
		final long lowerBitsMask = ( 1L << l ) - 1;
		final LongBigList lowerBitsList = ( lowerBits = LongArrayBitVector.getInstance() ).asLongBigList( l ).length( length );
		final BitVector upperBits = LongArrayBitVector.getInstance().length( length + ( upperBound >>> l ) + 1 );
		long last = Long.MIN_VALUE;
		for( long i = 0; i < length; i++ ) {
			v = iterator.nextLong();
			if ( v >= upperBound ) throw new IllegalArgumentException( "Too large value: " + v + " >= " + upperBound );
			if ( v < last ) throw new IllegalArgumentException( "Values are not nondecreasing: " + v + " < " + last );
			if ( l != 0 ) lowerBitsList.set( i, v & lowerBitsMask );
			upperBits.set( ( v >> l ) + i );
			last = v;
		}
		
		if ( iterator.hasNext() ) throw new IllegalArgumentException( "There are more than " + length + " values in the provided iterator" );
		
		selectUpper = new SimpleSelect( upperBits );
	}
	
	
	public long numBits() {
		return selectUpper.numBits() + selectUpper.bitVector().length() + lowerBits.length();
	}

	public long getLong( final long index ) {
		if ( index < 0 || index >= length ) throw new IndexOutOfBoundsException( Long.toString( index ) );
		final long start = index * l; 
		return ( selectUpper.select( index ) - index ) << l | lowerBits.getLong( start, start + l );
	}

	public long length() {
		return length;
	}
}
