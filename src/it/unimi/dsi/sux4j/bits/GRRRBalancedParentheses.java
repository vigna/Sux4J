package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;

public class GRRRBalancedParentheses implements BalancedParentheses {
	private static final long serialVersionUID = 1L;
	private static final boolean ASSERTS = true;
	private static final boolean DEBUG = true;
	private final long[] bits;
	private final long length;
	private LongArrayList farOpening;
	private LongArrayList farOpeningMatch;
	private LongArrayList farClosing;
	private LongArrayList farClosingMatch;

	public final static int countFarOpen( long word, int l ) {
		int c = 0, e = 0;
		while( l-- != 0 ) {
			if ( ( word & 1L << l ) != 0 ) {
				if ( ++e > 0 ) c++;
			}
			else {
				if ( e > 0 ) e = -1;
				else --e;
			}
		}

		return c;
	}

	public final static int findFarOpen( long word, int l, int k ) {
		int e = 0;
		while( l-- != 0 ) {
			if ( ( word & 1L << l ) != 0 ) {
				if ( ++e > 0 && k-- == 0 ) return l;
			}
			else {
				if ( e > 0 ) e = -1;
				else --e;
			}
		}

		return -1;
	}

	public final static int countFarClose( long word, int l ) {
		int c = 0, e = 0;
		for( int i = 0; i < l; i++ ) {
			if ( ( word & 1L << i ) != 0 ) {
				if ( e > 0 ) e = -1;
				else --e;
			}
			else {
				if ( ++e > 0 ) c++;
			}
		}

		return c;
	}

	public final static int findFarClose( long word, int l, int k ) {
		int e = 0;
		for( int i = 0; i < l; i++ ) {
			if ( ( word & 1L << i ) != 0 ) {
				if ( e > 0 ) e = -1;
				else --e;
			}
			else {
				if ( ++e > 0 && k-- == 0 ) return i;
			}
		}

		return -1;
	}


	public GRRRBalancedParentheses( final BitVector bv ) {
		this( bv.bits(), bv.length() );
	}
	
	public GRRRBalancedParentheses( final long[] bits, final long length ) {
		this.bits = bits;
		this.length = length;
		final int numWords = (int)( ( length + Long.SIZE - 1 ) / Long.SIZE );
		
		final byte count[] = new byte[ numWords ];
		final byte residual[] = new byte[ numWords ];
		farClosing = new LongArrayList();
		farClosingMatch = new LongArrayList();
		for( int i = 0; i < numWords; i++ ) {
			if ( DEBUG ) System.err.println( "Scanning word " + i + " (" + LongArrayBitVector.wrap( new long[] { bits[ i ] } ) + ")" );
			final int l = (int)Math.min( Long.SIZE, length - i * Long.SIZE );
			if ( i > 0 ) {
				int excess = 0, lastBlock = -1, lastResidual = -1;
				long lastPosition = -1;
				
				for( int j = 0; j < l; j++ ) {
					if ( ( bits[ i ] & 1L << j ) != 0 ) {
						if ( excess > 0 ) excess = -1;
						else --excess;
					}
					else {
						if ( ++excess > 0 ) {
							
							// Find block containing matching far open parenthesis
							int matchingBlock = i;
							while( count[ --matchingBlock ] == 0 );
							if ( lastBlock != -1 && matchingBlock != lastBlock ) {
								// This is a closing pioneer
								if ( DEBUG ) System.err.println( "+) " + lastPosition + " " + Arrays.toString(  count ) );
								farClosing.add( lastPosition );
								farClosingMatch.add( lastBlock * Long.SIZE + findFarOpen( bits[ lastBlock ], Long.SIZE, lastResidual ) );
							}
							lastPosition = i * Long.SIZE + j;
							lastBlock = matchingBlock;
							lastResidual = residual[ matchingBlock ];

							count[ matchingBlock ]--;
							residual[ matchingBlock ]++;
						}
					}
				}
				
				if ( lastPosition != -1 ) {
					// This is a closing pioneer
					if ( DEBUG ) System.err.println( "*) " + lastPosition + " " + Arrays.toString(  count ) );
					farClosing.add( lastPosition );
					farClosingMatch.add( lastBlock * Long.SIZE + findFarOpen( bits[ lastBlock ], Long.SIZE, lastResidual ) );
				}
				
			}
			count[ i ] = (byte)countFarOpen( bits[ i ], l );
			if ( DEBUG ) System.err.println( "Stack updated: " + Arrays.toString(  count ) );
		}
		
		for( int i = count.length; i-- != 0; ) if ( count[ i ] != 0 ) throw new IllegalArgumentException( "Unbalanced parentheses" );
		if ( DEBUG ) System.err.println( "):" + farClosing );
		if ( DEBUG ) System.err.println( "):" + farClosingMatch );
		
		ByteArrays.fill( residual, (byte)0 );
		farOpening = new LongArrayList();
		farOpeningMatch = new LongArrayList();
		for( int i = numWords; i-- != 0; ) {
			if ( DEBUG ) System.err.println( "Scanning word " + i + " (" + LongArrayBitVector.wrap( new long[] { bits[ i ] } ) + ")" );
			final int l = (int)Math.min( Long.SIZE, length - i * Long.SIZE );
			if ( i != numWords -1 ) {
				int excess = 0, lastBlock = -1, lastResidual = -1;
				long lastPosition = -1;
				
				for( int j = l; j-- != 0; ) {
					if ( ( bits[ i ] & 1L << j ) == 0 ) {
						if ( excess > 0 ) excess = -1;
						else --excess;
					}
					else {
						if ( ++excess > 0 ) {
							
							// Find block containing matching far close parenthesis
							int matchingBlock = i;
							while( count[ ++matchingBlock ] == 0 );
							if ( lastBlock != -1 && matchingBlock != lastBlock ) {
								// This is an opening pioneer
								if ( DEBUG ) System.err.println( "+( " + ( i * Long.SIZE + j ) + " " + Arrays.toString(  count ) );
								farOpening.add( lastPosition );
								farOpeningMatch.add( lastBlock * Long.SIZE + findFarClose( bits[ lastBlock ], Long.SIZE, lastResidual ) );
							}
							lastPosition = i * Long.SIZE + j;
							lastBlock = matchingBlock;
							lastResidual = residual[ matchingBlock ];
							
							count[ matchingBlock ]--;
							residual[ matchingBlock ]++;
						}
					}
				}				

				if ( lastPosition != -1 ) {
					// This is a opening pioneer
					if ( DEBUG ) System.err.println( "*( " + lastPosition + " " + Arrays.toString(  count ) );
					farOpening.add( lastPosition );
					farOpeningMatch.add( lastBlock * Long.SIZE + findFarClose( bits[ lastBlock ], Long.SIZE, lastResidual ) );
				}
			}
			count[ i ] = (byte)countFarClose( bits[ i ], l );
			if ( DEBUG ) System.err.println( "Stack updated: " + Arrays.toString(  count ) );
		}
		
		for( int i = count.length; i-- != 0; ) if ( count[ i ] != 0 ) throw new IllegalArgumentException( "Unbalanced parentheses" );
		if ( DEBUG ) System.err.println( "(:" + farOpening );
		if ( DEBUG ) System.err.println( "(:" + farOpeningMatch );
	}
	
	public BitVector bitVector() {
		return LongArrayBitVector.wrap( bits, length );
	}

	public long enclose( long pos ) {
		return 0;
	}

	public long findClose( final long pos ) {
		if ( DEBUG ) System.err.println( "findClose(" + pos + ")..." );
		final int word = (int)( pos / Long.SIZE );
		int bit = (int)( pos & LongArrayBitVector.WORD_MASK );
		if ( ( bits[ word ] & 1L << bit ) == 0 ) throw new IllegalArgumentException();
		
		int c = 1;
		while( ++bit < Long.SIZE ) {
			if ( ( bits[ word ] & 1L << bit ) == 0 ) c--;
			else c++;
			if ( c == 0 ) {
				if ( DEBUG ) System.err.println( "Returning in-word value: " + ( word * Long.SIZE + bit) );
				return word * Long.SIZE + bit;
			}
		}
		
		int i;
		for( i = 0; i < farOpening.size(); i++ ) if ( pos >= farOpening.getLong( i ) ) break;
		long pioneer = farOpening.getLong( i );
		long match = farOpeningMatch.getLong( i );
		if ( DEBUG ) System.err.println( "pioneer: " + pioneer + "; match: " + match );
		int dist = (int)( pos - pioneer );
		if ( pos == pioneer ) {
			if ( DEBUG ) System.err.println( "Returning exact pioneer match: " + match );
			return match;
		}
		
		if ( ASSERTS ) assert word == pioneer / Long.SIZE;
		if ( ASSERTS ) assert word != match / Long.SIZE;
		if ( ASSERTS ) assert pioneer < pos;
		
		int e = 2 * Fast.count( ( bits[ word ] >>> ( pioneer & LongArrayBitVector.WORD_MASK ) ) & ( 1L << dist ) - 1 ) - dist; 
		if ( ASSERTS ) {
			assert e >= 1;
			int ee = 0;
			for( long p = pioneer; p < pos; p++ ) if ( ( bits[ (int)( p / Long.SIZE ) ] & 1L << ( p % Long.SIZE ) ) != 0 ) ee++;
			else ee--;
			assert ee == e: ee + " != " + e;
		}
		if ( DEBUG ) System.err.println( "eccess: " + e );
		
		final int matchWord = (int)( match / Long.SIZE );
		
		for( int j = (int)( match & LongArrayBitVector.WORD_MASK ); j-- != 0; ) {
			if ( ( bits[ matchWord ] & 1L << j ) != 0 ) e++;
			else e--;
			if ( e == 0 ) {
				if ( DEBUG ) System.err.println( "Returning value: " + ( matchWord * Long.SIZE + j ) );
				return matchWord * Long.SIZE + j;
			}
		}
		
		throw new IllegalArgumentException();
	}

	public long findOpen( long pos ) {
		
		throw new IllegalArgumentException();
}

	public long numBits() {
		return 0;
	}

}
