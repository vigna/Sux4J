package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Collections;

import static it.unimi.dsi.bits.Fast.ONES_STEP_4;
import static it.unimi.dsi.bits.Fast.ONES_STEP_8;
import static it.unimi.dsi.bits.Fast.MSBS_STEP_8;

public class GRRRBalancedParentheses implements BalancedParentheses {
	private static final long serialVersionUID = 1L;
	private static final boolean ASSERTS = true;
	private static final boolean DEBUG = false;
	private transient long[] bits;
	protected final BitVector bitVector;
	private final SparseSelect openingPioneers;
	private final SparseRank openingPioneersRank;
	private final EliasFanoLongBigList openingPioneerMatches;
	private final SparseSelect closingPioneers;
	private final SparseRank closingPioneersRank;
	private final EliasFanoLongBigList closingPioneerMatches;

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
	
	private final static long L = 0x4038302820181008L;

	public final static int findNearClose( final long word ) {
		long byteSums = word - ( ( word & 0xa * ONES_STEP_4 ) >>> 1 );
		long zeroes, update;
		byteSums = ( byteSums & 3 * ONES_STEP_4 ) + ( ( byteSums >>> 2 ) & 3 * ONES_STEP_4 );
		//System.err.print( "**** " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(byteSums >>> i * 8 & 0xFF) + " " ); System.err.println();
        byteSums = ( ( byteSums + ( byteSums >>> 4 ) ) & 0x0f * ONES_STEP_8 ) * ONES_STEP_8 << 1; // Twice the number of open parentheses (cumulative by byte)

		//System.err.print( "**** " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(byteSums >>> i * 8 & 0xFF) + " " ); System.err.println();
        
		byteSums = ( ( L | MSBS_STEP_8 ) - byteSums ) ^ ( ( L ^ ~byteSums ) & MSBS_STEP_8 ); // Closed excess per byte
		//System.err.print( "Closed excess: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(byteSums >>> i * 8 & 0xFF) + " " ); System.err.println();

		// Set up flags for excess values that are already zero
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		//System.err.print( "Updates: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(update >>> i * 8 & 0xFF) + " " ); System.err.println();
		zeroes = ( MSBS_STEP_8 | ONES_STEP_8 * 7 ) & update;
		//System.err.print( "Zeroes: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(zeroes >>> i * 8 & 0xFF) + " " ); System.err.println();
		
		byteSums += ( word >>> 7 & ONES_STEP_8 );
		byteSums = ( ( byteSums | MSBS_STEP_8 ) - ( ~( word >>> 7 ) & ONES_STEP_8 ) ) ^ ( ( byteSums ^ MSBS_STEP_8 ) & MSBS_STEP_8 );
		//System.err.print( "Sums: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(byteSums >>> i * 8 & 0xFF) + " " ); System.err.println();
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		//System.err.print( "Updates: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(update >>> i * 8 & 0xFF) + " " ); System.err.println();
		zeroes = zeroes & ~update | ( MSBS_STEP_8 | ONES_STEP_8 * 6 ) & update;
		//System.err.print( "Zeroes: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(zeroes >>> i * 8 & 0xFF) + " " ); System.err.println();
		
		byteSums += ( word >>> 6 & ONES_STEP_8 );
		byteSums = ( ( byteSums | MSBS_STEP_8 ) - ( ~( word >>> 6 ) & ONES_STEP_8 ) ) ^ ( ( byteSums ^ MSBS_STEP_8 ) & MSBS_STEP_8 ); 
		//System.err.print( "Sums: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(byteSums >>> i * 8 & 0xFF) + " " ); System.err.println();
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		//System.err.print( "Updates: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(update >>> i * 8 & 0xFF) + " " ); System.err.println();
		zeroes = zeroes & ~update | ( MSBS_STEP_8 | ONES_STEP_8 * 5 ) & update;
		//System.err.print( "Zeroes: " ); for( int i = 0; i < 8; i++ ) System.err.print( (byte)(zeroes >>> i * 8 & 0xFF) + " " ); System.err.println();
		
		byteSums += ( word >>> 5 & ONES_STEP_8 );
		byteSums = ( ( byteSums | MSBS_STEP_8 ) - ( ~( word >>> 5 ) & ONES_STEP_8 ) ) ^ ( ( byteSums ^ MSBS_STEP_8 ) & MSBS_STEP_8 ); 
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		zeroes = zeroes & ~update | ( MSBS_STEP_8 | ONES_STEP_8 * 4 ) & update;
		
		byteSums += ( word >>> 4 & ONES_STEP_8 );
		byteSums = ( ( byteSums | MSBS_STEP_8 ) - ( ~( word >>> 4 ) & ONES_STEP_8 ) ) ^ ( ( byteSums ^ MSBS_STEP_8 ) & MSBS_STEP_8 ); 
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		zeroes = zeroes & ~update | ( MSBS_STEP_8 | ONES_STEP_8 * 3 ) & update;
		
		byteSums += ( word >>> 3 & ONES_STEP_8 );
		byteSums = ( ( byteSums | MSBS_STEP_8 ) - ( ~( word >>> 3 ) & ONES_STEP_8 ) ) ^ ( ( byteSums ^ MSBS_STEP_8 ) & MSBS_STEP_8 ); 
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		zeroes = zeroes & ~update | ( MSBS_STEP_8 | ONES_STEP_8 * 2 ) & update;
		
		byteSums += ( word >>> 2 & ONES_STEP_8 );
		byteSums = ( ( byteSums | MSBS_STEP_8 ) - ( ~( word >>> 2 ) & ONES_STEP_8 ) ) ^ ( ( byteSums ^ MSBS_STEP_8 ) & MSBS_STEP_8 ); 
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		zeroes = zeroes & ~update | ( MSBS_STEP_8 | ONES_STEP_8 * 1 ) & update;
		
		byteSums += ( word >>> 1 & ONES_STEP_8 );
		byteSums = ( ( byteSums | MSBS_STEP_8 ) - ( ~( word >>> 1 ) & ONES_STEP_8 ) ) ^ ( ( byteSums ^ MSBS_STEP_8 ) & MSBS_STEP_8 ); 
		update = ( ~( byteSums | ( ( byteSums | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7;
		update = ( ( update | MSBS_STEP_8 ) - ONES_STEP_8 ) ^ ( ( update ^ ONES_STEP_8 ) | ~MSBS_STEP_8 );
		zeroes = zeroes & ~update | MSBS_STEP_8 & update;
		
		
		//for( int i = 0; i < 8; i++ ) System.err.print( (byte)(byteSums >>> i * 8 & 0xFF) + " " ); System.err.println();
		//for( int i = 0; i < 8; i++ ) System.err.print( (byte)(zeroes >>> i * 8 & 0xFF) + " " ); System.err.println();
		
		
		final int block = Fast.leastSignificantBit( zeroes >>> 7 & ONES_STEP_8 );
		// A simple trick to return 127 if block < 0 (i.e., no match)
		return ( (int)( block + ( zeroes >>> block & 0x7F ) ) | ( block >> 8 ) ) & 0x7F;

		/*		//assert block != -1;
		//block = block == -1 ? 0 : block / 8;
		assert block >= 7;
		excess = excess >>> block - 7 & 0xFF;
		System.out.println( "LSB: " + Fast.leastSignificantBit( zeroes & MSBS_STEP_8 ) + " Block: " + block ); 
		System.out.println("Excess: " + excess );
		
		if ( excess != 0 ) {
			for( int i = 0; i < 8; i++ ) {
				if ( ( origWord & 1L << block - 7 + i ) != 0 ) excess++;
				else excess--;
				if ( excess == 0 ) {
					assert ( zeroes >>> block -7 & 0x7F ) - 1 == i : (( zeroes >>> block -7 & 0x7F ) - 1 )+ " != " + i;

					return block -7 + i;
				}
			}
		}
		else {
			//assert ( zeroes >>> block -7 & 0x7F ) - 1 == 1 : ( ( zeroes >>> block -7 & 0x7F ) - 1 ) + " != " + 1;
			return (int)( block - 7 + ( zeroes >>> block -7 & 0x7F ) - 1 );
		}
		
        return -1;*/
        
	}
	
	public GRRRBalancedParentheses( final BitVector bv ) {
		this( bv, true, true, true );
	}
	
	public GRRRBalancedParentheses( final long[] bits, final long length ) {
		this( LongArrayBitVector.wrap(  bits, length ) );
	}

	public GRRRBalancedParentheses( final BitVector bitVector, final boolean findOpen, final boolean findClose, final boolean enclose ) {
		if ( ! findOpen && ! findClose && ! enclose ) throw new IllegalArgumentException( "You must specify at least one implemented method" );
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();
		final int numWords = (int)( ( length + Long.SIZE - 1 ) / Long.SIZE );
		
		final byte count[] = new byte[ numWords ];
		final byte residual[] = new byte[ numWords ];

		LongArrayList closingPioneers = null, closingPioneerMatches = null, openingPioneers = null, openingPioneerMatches = null;

		if ( findOpen ) {
			closingPioneers = new LongArrayList();
			closingPioneerMatches = new LongArrayList();
			for( int block = 0; block < numWords; block++ ) {
				if ( DEBUG ) System.err.println( "Scanning word " + block + " (" + LongArrayBitVector.wrap( new long[] { bits[ block ] } ) + ")" );
				final int l = (int)Math.min( Long.SIZE, length - block * Long.SIZE );

				if ( block > 0 ) {
					int excess = 0;
					int countFarClosing = countFarClose( bits[ block ], l );

					for( int j = 0; j < l; j++ ) {
						if ( ( bits[ block ] & 1L << j ) != 0 ) {
							if ( excess > 0 ) excess = -1;
							else --excess;
						}
						else {
							if ( ++excess > 0 ) {
								// Find block containing matching far open parenthesis
								int matchingBlock = block;
								while( count[ --matchingBlock ] == 0 );
								countFarClosing--;
								if ( --count[ matchingBlock ] == 0 || countFarClosing == 0 ) {
									// This is a closing pioneer
									if ( DEBUG ) System.err.println( "+) " + ( block * Long.SIZE + j ) + " " + Arrays.toString(  count ) );
									closingPioneers.add( block * Long.SIZE + j );
									closingPioneerMatches.add( ( block * Long.SIZE + j ) - ( matchingBlock * Long.SIZE + findFarOpen( bits[ matchingBlock ], Long.SIZE, residual[ matchingBlock ] ) ) );
								}
								residual[ matchingBlock ]++;
							}
						}
					}
				}
				count[ block ] = (byte)countFarOpen( bits[ block ], l );
				if ( DEBUG ) System.err.println( "Stack updated: " + Arrays.toString(  count ) );
			}

			for( int i = count.length; i-- != 0; ) if ( count[ i ] != 0 ) throw new IllegalArgumentException( "Unbalanced parentheses" );
			if ( DEBUG ) System.err.println( "):" + closingPioneers );
			if ( DEBUG ) System.err.println( "):" + closingPioneerMatches );
		}

		if ( findClose ) {
			ByteArrays.fill( residual, (byte)0 );

			openingPioneers = new LongArrayList();
			openingPioneerMatches = new LongArrayList();

			for( int block = numWords; block-- != 0; ) {
				if ( DEBUG ) System.err.println( "Scanning word " + block + " (" + LongArrayBitVector.wrap( new long[] { bits[ block ] } ) + ")" );
				final int l = (int)Math.min( Long.SIZE, length - block * Long.SIZE );

				if ( block != numWords -1 ) {
					int excess = 0;
					int countFarOpening = countFarOpen( bits[ block ], l );
					boolean somethingAdded = false;

					for( int j = l; j-- != 0; ) {
						if ( ( bits[ block ] & 1L << j ) == 0 ) {
							if ( excess > 0 ) excess = -1;
							else --excess;
						}
						else {
							if ( ++excess > 0 ) {
								// Find block containing matching far close parenthesis
								int matchingBlock = block;
								while( count[ ++matchingBlock ] == 0 );
								countFarOpening--;
								if ( --count[ matchingBlock ] == 0 || countFarOpening == 0 ) {
									// This is an opening pioneer
									if ( DEBUG ) System.err.println( "+( " + ( block * Long.SIZE + j ) + " " + Arrays.toString(  count ) );
									openingPioneers.add( block * Long.SIZE + j );
									openingPioneerMatches.add( - ( block * Long.SIZE + j ) + ( matchingBlock * Long.SIZE + findFarClose( bits[ matchingBlock ], Long.SIZE, residual[ matchingBlock ]) ) );
									//if ( block == 14 ) System.err.println( "Adding " + block * Long.SIZE + j );
									if ( ASSERTS ) somethingAdded = true;
								}
								residual[ matchingBlock ]++;
							}
						}					
					}				
					if ( ASSERTS ) assert somethingAdded || countFarOpen( bits[ block ], l ) == 0 : "No pioneers for block " + block + " " + LongArrayBitVector.wrap(  new long[] { bits[ block ] }, l ) + " (" + l + ") " + countFarOpen( bits[ block ], l );
				}
				count[ block ] = (byte)countFarClose( bits[ block ], l );
				if ( DEBUG ) System.err.println( "Stack updated: " + Arrays.toString(  count ) );
			}

			for( int i = count.length; i-- != 0; ) if ( count[ i ] != 0 ) throw new IllegalArgumentException( "Unbalanced parentheses" );
			if ( DEBUG ) System.err.println( "(:" + openingPioneers );
			if ( DEBUG ) System.err.println( "(:" + openingPioneerMatches );

			Collections.reverse( openingPioneers );
			Collections.reverse( openingPioneerMatches );
		}

		this.closingPioneers = closingPioneers != null ? new SparseSelect( closingPioneers ) : null;
		this.closingPioneersRank = closingPioneers != null ? this.closingPioneers.getRank() : null;
		this.closingPioneerMatches = closingPioneers != null ? new EliasFanoLongBigList( closingPioneerMatches ) : null;
		this.openingPioneers = openingPioneers != null ? new SparseSelect( openingPioneers ) : null;
		this.openingPioneersRank = openingPioneers != null ? this.openingPioneers.getRank() : null;
		this.openingPioneerMatches = openingPioneers != null ? new EliasFanoLongBigList( openingPioneerMatches ) : null;
}
	
	public long enclose( long pos ) {
		throw new UnsupportedOperationException();
	}

	public long findClose( final long pos ) {
		if ( DEBUG ) System.err.println( "findClose(" + pos + ")..." );
		final int word = (int)( pos / Long.SIZE );
		int bit = (int)( pos & LongArrayBitVector.WORD_MASK );
		if ( ( bits[ word ] & 1L << bit ) == 0 ) throw new IllegalArgumentException();

		int result = findNearClose( bits[ word ] >>> bit );

		if ( ASSERTS ) {
			int c = 1;
			int b = bit;
			while( ++b < Long.SIZE ) {
				if ( ( bits[ word ] & 1L << b ) == 0 ) c--;
				else c++;
				if ( c == 0 ) break;
			}

			if ( ASSERTS ) assert ( c != 0 ) == ( result < 0 || result >= Long.SIZE - bit ) : "c: " + c + " bit: " + (b - bit) + " result:" + result + " " + LongArrayBitVector.wrap(  new long[] { bits[ word ] >>> bit }, Long.SIZE - bit ) + " (" +(Long.SIZE -bit)+ ")";
			if ( ASSERTS ) assert ( c != 0 ) || ( b == bit + result ) : b + " != " + ( bit + result ) + " (bit:" + bit + ")" + LongArrayBitVector.wrap(  new long[] { bits[ word ] >>> bit } );
		}
		if ( result < Long.SIZE - bit ) {
			if ( DEBUG ) System.err.println( "Returning in-word value: " + ( word * Long.SIZE + bit + result ) );
			return word * Long.SIZE + bit + result;
		}

		final long pioneerIndex = openingPioneersRank.rank( pos + 1 ) - 1;
		final long pioneer = openingPioneers.select( pioneerIndex );
		final long match = pioneer + openingPioneerMatches.getLong( pioneerIndex );

		if ( pos == pioneer ) {
			if ( DEBUG ) System.err.println( "Returning exact pioneer match: " + match );
			return match;
		}
		
		if ( DEBUG ) System.err.println( "pioneer: " + pioneer + "; match: " + match );
		int dist = (int)( pos - pioneer );
		
		if ( ASSERTS ) assert word == pioneer / Long.SIZE : "pos: " + pos + " word:" + word + " pioneer: " + pioneer + " word:" + pioneer / Long.SIZE  + " result:" + result;
		if ( ASSERTS ) assert word != match / Long.SIZE;
		if ( ASSERTS ) assert pioneer < pos;
		
		int e = 2 * Fast.count( ( bits[ word ] >>> ( pioneer % Long.SIZE ) ) & ( 1L << dist ) - 1 ) - dist; 
		if ( ASSERTS ) {
			assert e >= 1;
			int ee = 0;
			for( long p = pioneer; p < pos; p++ ) if ( ( bits[ (int)( p / Long.SIZE ) ] & 1L << ( p % Long.SIZE ) ) != 0 ) ee++;
			else ee--;
			assert ee == e: ee + " != " + e;
		}
		if ( DEBUG ) System.err.println( "eccess: " + e );
		
		final int matchWord = (int)( match / Long.SIZE );
		final int matchBit = (int)( match % Long.SIZE );
		
		final int numFarClose = matchBit - 2 * Fast.count( bits[ matchWord ] & ( 1L << matchBit ) - 1 );

		if ( DEBUG ) System.err.println( "far close before match: " + numFarClose );
		if ( DEBUG ) System.err.println( "Finding far close of rank " + ( numFarClose - e ) + " at " + findFarClose( bits[ matchWord ], matchBit, numFarClose - e ) );		
		if ( DEBUG ) System.err.println( "Returning value: " + ( matchWord * Long.SIZE + findFarClose( bits[ matchWord ], matchBit, numFarClose - e ) ) );
		return matchWord * Long.SIZE + findFarClose( bits[ matchWord ], matchBit, numFarClose - e );
	}

	public long findOpen( long pos ) {
		throw new UnsupportedOperationException();
	}

	public long numBits() {
		return 
			( openingPioneers != null ? ( openingPioneers.numBits() + openingPioneersRank.numBits() + openingPioneerMatches.numBits() ) : 0 ) + 
			( closingPioneers != null ? closingPioneers.numBits() + closingPioneersRank.numBits() + closingPioneerMatches.numBits() : 0 );
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		bits = bitVector.bits();
	}

	public BitVector bitVector() {
		return bitVector;
	}

}
