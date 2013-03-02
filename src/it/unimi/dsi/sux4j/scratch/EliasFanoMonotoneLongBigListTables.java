package it.unimi.dsi.sux4j.scratch;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2013 Sebastiano Vigna 
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

import static it.unimi.dsi.bits.Fast.MSBS_STEP_8;
import static it.unimi.dsi.bits.Fast.ONES_STEP_4;
import static it.unimi.dsi.bits.Fast.ONES_STEP_8;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.sux4j.bits.SimpleSelect;

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
 * <p>This implementation uses tables recording the position of one each 2<sup>{@value #LOG_2_QUANTUM}</sup>
 * ones and zeroes.
 */

public class EliasFanoMonotoneLongBigListTables extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 3L;
	public final static int LOG_2_QUANTUM = 9;
	
	/** The length of the sequence. */
	protected final long length;
	/** The number of lower bits. */
	protected final int l;
	/** The mask for lower bits. */
	protected final long mask;
	/** The lower bits of each element, stored explicitly. */
	protected final long[] lowerBits;
	/** The upper bits. */
	protected final long[] upperBits;
	/** The skips for ones (the <var>k</var>-th element contains the position of the ({@link #quantum}<var>k</var>)-th one). */
	protected final long[] skipToOne;
	/** The skips for zeroes (the <var>k</var>-th element contains the position of the ({@link #quantum}<var>k</var>)-th zero). */
	protected final long[] skipToZero;
	/** The indexing quantum. */
	protected final int quantum;
	/** The base-2 logarithm of {@link #quantum}. */
	protected final int log2Quantum;
	
	protected EliasFanoMonotoneLongBigListTables( final long length, final int l, final long skipToOne[], final long[] skipToZero, final long[] upperBits, final long[] lowerBits ) {
		this.length = length;
		this.l = l;
		this.mask = ( 1L << l ) - 1;
		this.lowerBits = lowerBits;
		this.upperBits = upperBits;
		this.skipToOne = skipToOne;
		this.skipToZero = skipToZero;
		this.log2Quantum = LOG_2_QUANTUM;
		this.quantum = 1 << LOG_2_QUANTUM;
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 * 
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigListTables( final IntIterable list ) {
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
	public EliasFanoMonotoneLongBigListTables( final ShortIterable list ) {
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
	public EliasFanoMonotoneLongBigListTables( final ByteIterable list ) {
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

	public EliasFanoMonotoneLongBigListTables( final LongIterable list ) {
		this( computeParameters( list.iterator() ), list.iterator() );
	}

	/** Computes the number of elements and the last element returned by the given iterator.
	 * 
	 * 
	 * @param iterator an iterator.
	 * @return a two-element array of longs containing the number of elements returned by
	 * the iterator and the last returned element, respectively.
	 */
	private static long[] computeParameters( final LongIterator iterator ) {
		long v = -1, prev = -1, c = 0;
		while( iterator.hasNext() ) {
			v = iterator.nextLong();
			if ( prev > v ) throw new IllegalArgumentException( "The list of values is not monotone: " + prev + " > " + v );
			prev = v;
			c++;
		}
		return new long[] { c, v };
	}
	

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be 
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigListTables( final long n, final long upperBound, final ByteIterator iterator ) {
		this( new long[] { n, upperBound }, LongIterators.wrap( iterator ) );
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be 
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigListTables( final long n, final long upperBound, final ShortIterator iterator ) {
		this( new long[] { n, upperBound }, LongIterators.wrap( iterator ) );
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be 
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigListTables( final long n, final long upperBound, final IntIterator iterator ) {
		this( new long[] { n, upperBound }, LongIterators.wrap( iterator ) );
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 * 
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 * 
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be 
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigListTables( final long n, final long upperBound, final LongIterator iterator ) {
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
	protected EliasFanoMonotoneLongBigListTables( long[] a, final LongIterator iterator ) {
		length = a[ 0 ];
		this.log2Quantum = LOG_2_QUANTUM;
		this.quantum = 1 << LOG_2_QUANTUM;
		long v = -1;
		final long upperBound = a[ 1 ];
		l = length == 0 ? 0 : Math.max( 0, Fast.mostSignificantBit( upperBound / length ) );
		mask = ( 1L << l ) - 1;
		final long lowerBitsMask = ( 1L << l ) - 1;
		final LongArrayBitVector lowerBitVector = LongArrayBitVector.getInstance();
		final LongBigList lowerBitsList = lowerBitVector.asLongBigList( l );
		lowerBitsList.size( length );
		final BitVector upperBitVector = LongArrayBitVector.getInstance().length( length + ( upperBound >>> l ) + 1 );
		long last = Long.MIN_VALUE;
		for( long i = 0; i < length; i++ ) {
			v = iterator.nextLong();
			if ( v > upperBound ) throw new IllegalArgumentException( "Too large value: " + v + " >= " + upperBound );
			if ( v < last ) throw new IllegalArgumentException( "Values are not nondecreasing: " + v + " < " + last );
			if ( l != 0 ) lowerBitsList.set( i, v & lowerBitsMask );
			upperBitVector.set( ( v >>> l ) + i );
			last = v;
		}
		
		if ( iterator.hasNext() ) throw new IllegalArgumentException( "There are more than " + length + " values in the provided iterator" );
		this.lowerBits = lowerBitVector.bits();
		this.upperBits = upperBitVector.bits();

		if ( length == 0 ) {
			skipToOne = skipToZero = null;
		}
		else {
			skipToZero = new long[ (int)( ( upperBound >>> l ) >>> log2Quantum ) ];
			skipToOne = new long[ (int)( length - 1 >>> log2Quantum ) ];
		}
		
		int po = 0, pz = 0;
		for( long i = 0, cz = 0, co=0; i < upperBitVector.length(); i++ ) {
			final boolean bit = upperBitVector.getBoolean( i );
			if ( bit ) {
				if ( co != 0 && co % quantum == 0 ) skipToOne[ po++ ] = i;
				co++;
			}
			else {
				if ( cz != 0 && cz % quantum == 0 ) skipToZero[ pz++ ] = i;
				cz++;
			}
		}
		
		assert po == skipToOne.length : po + " != " + skipToOne.length;
		assert pz == skipToZero.length: pz + " != " + skipToZero.length;;
	}
	
	
	public long numBits() {
		return ( (long)upperBits.length + lowerBits.length + ( skipToOne != null ? skipToOne.length : 0 ) + ( skipToZero != null ? skipToZero.length : 0 ) ) * Long.SIZE;
	}
	
	public long getLong( final long index ) {
		long delta = index;
		int curr = 0;
		long window;
		
		if ( index >= quantum ) {
			final long block = index >>> log2Quantum;
			assert block > 0;
			assert block <= skipToOne.length;
			final long position = skipToOne[ (int)( block - 1 ) ];
			window = upperBits[ curr = (int)( position / Long.SIZE ) ] & -1L << (int)( position );
			delta = index - ( block << log2Quantum );
		}
		else window = upperBits[ curr = 0 ];

		for( int bitCount; ( bitCount = Long.bitCount( window ) ) <= delta; delta -= bitCount )
			window = upperBits[ ++curr ];
		
		assert window != 0;
		final int select;
		/* This appears to be faster than != 0 (WTF?!). Note that for delta == 1 the following code is a NOP. */
		if ( delta != 0 ) {
			// Phase 1: sums by byte
			final long word = window;
			assert delta < Long.bitCount( word ) : delta + " >= " + Long.bitCount( word );
			long byteSums = word - ( ( word & 0xa * ONES_STEP_4 ) >>> 1 );
			byteSums = ( byteSums & 3 * ONES_STEP_4 ) + ( ( byteSums >>> 2 ) & 3 * ONES_STEP_4 );
			byteSums = ( byteSums + ( byteSums >>> 4 ) ) & 0x0f * ONES_STEP_8;
			byteSums *= ONES_STEP_8;

			// Phase 2: compare each byte sum with delta to obtain the relevant byte
			final long rankStep8 = delta * ONES_STEP_8;
			final long byteOffset = ( ( ( ( ( rankStep8 | MSBS_STEP_8 ) - byteSums ) & MSBS_STEP_8 ) >>> 7 ) * ONES_STEP_8 >>> 53 ) & ~0x7;

			final int byteRank = (int)( delta - ( ( ( byteSums << 8 ) >>> byteOffset ) & 0xFF ) );

			select = (int)( byteOffset + Fast.selectInByte[ (int)( word >>> byteOffset & 0xFF ) | byteRank << 8 ] );
		}
		else select = Long.numberOfTrailingZeros( window );

		final long upperBits = curr * Long.SIZE + select - index << l;
		
		if ( l == 0 ) return upperBits;
		
		final long position = index * l; 
		final int startWord = (int)( position / Long.SIZE );
		final int startBit = (int)( position % Long.SIZE );
		final int totalOffset = startBit + l;
		final long result = lowerBits[ startWord ] >>> startBit;
		return upperBits | ( totalOffset <= Long.SIZE ? result : result | lowerBits[ startWord + 1 ] << -startBit ) & mask;
	}

	public long size64() {
		return length;
	}
}
