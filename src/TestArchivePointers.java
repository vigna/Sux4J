import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.mg4j.io.InputBitStream;
import it.unimi.dsi.sux4j.bits.SparseSelect;

import java.io.IOException;

public class TestArchivePointers {

	
	@SuppressWarnings("unchecked")
	public static void main( String a[] ) throws IOException {
		final InputBitStream skips = new InputBitStream( a[ 0 ] );
		final int n = Integer.parseInt( a[ 1 ] );
		final int m = Integer.parseInt( a[ 2 ] );
		
		SparseSelect sparse = new SparseSelect( n, m, new AbstractLongIterator() {
			int i = -1;
			long curr = 0;

			public long nextLong() {
				i++;
				try {
					skips.readZeta(3) ;
					return curr += skips.readLongGamma();
				}
				catch ( IOException e ) {
					throw new RuntimeException( e );
				}
			}

			public boolean hasNext() {
				return i < n;
			}
		} );
		
		System.out.println( "Bits per pointer: " + (double)sparse.numBits() / n );
	}
}
