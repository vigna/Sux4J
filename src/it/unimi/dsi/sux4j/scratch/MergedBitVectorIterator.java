package it.unimi.dsi.sux4j.scratch;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** An iterator returning the union of the bit vectors returned by two iterators.
 *  The two iterators must return bit vectors in an increasing fashion; the resulting
 *  {@link MergedBitVectorIterator} will do the same. Duplicates will be eliminated.
 */

public class MergedBitVectorIterator<T> extends AbstractObjectIterator<BitVector> {
	/** The first component iterator. */
	private final Iterator<? extends BitVector> it0;
	/** The second component iterator. */
	private final Iterator<? extends BitVector> it1;
	/** The last bit vector returned by {@link #it0}. */
	private BitVector curr0;
	/** The last bit vector returned by {@link #it1}. */
	private BitVector curr1;
	/** The result. */
	private LongArrayBitVector result;
	
	/** Creates a new merged iterator by merging two given iterators.
	 * 
	 * @param it0 the first (monotonically nondecreasing) component iterator.
	 * @param it1 the second (monotonically nondecreasing) component iterator.
	 */
	public MergedBitVectorIterator( final Iterator<? extends BitVector> it0, final Iterator<? extends BitVector> it1 ) {
		this.it0 = it0;
		this.it1 = it1;
		result = LongArrayBitVector.getInstance();
		if ( it0.hasNext() ) curr0 = it0.next();
		if ( it1.hasNext() ) curr1 = it1.next();
	}

	public boolean hasNext() {
		return curr0 != null || curr1 != null;
	}
	
	public BitVector next() {
		if ( ! hasNext() ) throw new NoSuchElementException();

		final int cmp;
		
		if ( curr0 == null ) {
			result.replace( curr1 );
			curr1 = it1.hasNext() ? it1.next() : null;
		} 
		else if ( curr1 == null ) {
			result.replace( curr0 );
			curr0 = it0.hasNext() ? it0.next() : null;
		} 
		else if ( ( cmp = curr0.compareTo( curr1 ) ) < 0 ) {
			result.replace( curr0 );
			curr0 = it0.hasNext() ? it0.next() : null;
		} 
		else if ( cmp > 0 ) {
			result.replace( curr1 );
			curr1 = it1.hasNext() ? it1.next() : null;
		} 
		else {
			result.replace( curr1 );
			curr0 = it0.hasNext() ? it0.next() : null;
			curr1 = it1.hasNext() ? it1.next() : null;
		}
		
		return result;
	}
}