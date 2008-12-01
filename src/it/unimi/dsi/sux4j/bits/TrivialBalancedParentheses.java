package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;

public class TrivialBalancedParentheses implements BalancedParentheses {
	private static final long serialVersionUID = 1L;
	private final BitVector v;

	public TrivialBalancedParentheses( BitVector v ) {
		this.v = v;
	}
	
	public BitVector bitVector() {
		return v;
	}

	public long enclose( long pos ) {
		throw new UnsupportedOperationException();
	}

	public long findClose( long pos ) {
		if ( ! v.getBoolean( pos ) ) throw new IllegalArgumentException();
		int c = 1;
		while( ++pos < v.length() ) {
			if ( ! v.getBoolean( pos ) ) c--;
			else c++;
			if ( c == 0 ) return pos;
		}
		
		throw new IllegalArgumentException();
	}

	public long findOpen( long pos ) {
		if ( v.getBoolean( pos ) ) throw new IllegalArgumentException();

		int c = 1;
		while( --pos >= 0 ) {
			if ( ! v.getBoolean( pos ) ) c++;
			else c--;
			if ( c == 0 ) return pos;
		}
		
		throw new IllegalArgumentException();
	}

	public long numBits() {
		return 0;
	}
}
