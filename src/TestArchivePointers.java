import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.sux4j.bits.SparseRank;
import it.unimi.dsi.sux4j.bits.SparseSelect;

import java.io.IOException;

public class TestArchivePointers {

	
	@SuppressWarnings("unchecked")
	public static void main( String a[] ) throws IOException {
		final InputBitStream skips = new InputBitStream( a[ 0 ] );
		final long n = Long.parseLong( a[ 1 ] );
		final long m = Long.parseLong( a[ 2 ] );
		final long[] doc = new long[ (int)m ];
		final int[] p = new int[ 1 ];
		
		SparseSelect sparse = new SparseSelect( n, m, new AbstractLongIterator() {
			int i = 0;
			long curr = 0;
			
			public long nextLong() {
				i++;
				try {
					final int s = skips.readZeta(3);
					doc[ p[0]++ ] = s;
					
					return curr += skips.readLongGamma();
				}
				catch ( IOException e ) {
					throw new RuntimeException( e );
				}
			}

			public boolean hasNext() {
				return i < m;
			}
		} );

		skips.position( 0 );
		long numDocs = doc[ p[0] - 1 ] + 1;

//		SimpleSelectZero sparse2 = new SimpleSelectZero( currDoc[ 0 ] + 1, p[0], it.unimi.dsi.fastutil.longs.LongArrayList.wrap( doc, p[0] ).iterator() );
		LongArrayBitVector v = LongArrayBitVector.getInstance().length( numDocs );
		for( int i = p[0]; i-- != 0; ) v.set( doc[ i ] );
		v.flip();
		SparseRank sparse2 = new SparseRank( v );

		skips.position( 0 );
		long curr = 0, skip;
		for( int i = 0; i < m; i++ ) {
			skip = skips.readZeta(3);
			assert i == sparse2.rankZero( skip ) : "At " + i + ": (skip=" + skip + "):" + i + " != " + sparse2.rankZero( skip );
			curr += skips.readLongGamma();
			assert curr == sparse.select( i ) : "At " + i + ": " + curr + " != " + sparse.select( i );
		}
		
		System.out.println( "Bits per pointer: " + (double)sparse.numBits() / m );
		System.out.println( "Bits per identifier: " + (double)sparse2.numBits() / numDocs );

		BinIO.storeObject( sparse, "uk.offsets" );
		BinIO.storeObject( sparse2, "uk.docs" );
	}
}
