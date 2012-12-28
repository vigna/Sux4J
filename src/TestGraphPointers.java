import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.sux4j.bits.SparseSelect;

import java.io.IOException;

public class TestGraphPointers {

	
	public static void main( String a[] ) throws IOException {
		@SuppressWarnings("resource")
		final InputBitStream skips = new InputBitStream( a[ 0 ] );
		final long n = Long.parseLong( a[ 1 ] );
		final long m = Long.parseLong( a[ 2 ] );
		
		SparseSelect sparse = new SparseSelect( n, m, new AbstractLongIterator() {
			int i = 0;
			long curr = 0;
			
			public long nextLong() {
				i++;
				try {
					return curr += skips.readGamma() ; 
				}
				catch ( IOException e ) {
					throw new RuntimeException( e );
				}
			}

			public boolean hasNext() {
				return i < m;
			}
		} );
		
		System.out.println( "Bits per pointer: " + (double)sparse.numBits() / m );
	}
}
