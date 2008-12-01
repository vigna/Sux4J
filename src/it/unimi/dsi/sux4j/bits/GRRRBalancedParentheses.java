package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;

import java.util.Arrays;
import java.util.Collections;

public class GRRRBalancedParentheses implements BalancedParentheses {
	private static final long serialVersionUID = 1L;
	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;
	private final long[] bits;
	private final long length;
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


	public GRRRBalancedParentheses( final BitVector bv ) {
		this( bv.bits(), bv.length() );
	}
	
	public GRRRBalancedParentheses( final BitVector bv, final boolean findOpen, final boolean findClose, final boolean enclose ) {
		this( bv.bits(), bv.length(), findOpen, findClose, enclose );
	}
	
	public GRRRBalancedParentheses( final long[] bits, final long length ) {
		this( bits, length, true, true, true );
	}

	public GRRRBalancedParentheses( final long[] bits, final long length, final boolean findOpen, final boolean findClose, final boolean enclose ) {
		if ( ! findOpen && ! findClose && ! enclose ) throw new IllegalArgumentException( "You must specify at least one implemented method" );
		this.bits = bits;
		this.length = length;
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
	
	public BitVector bitVector() {
		return LongArrayBitVector.wrap( bits, length );
	}

	public long enclose( long pos ) {
		throw new UnsupportedOperationException();
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
		
		final long pioneerIndex = openingPioneersRank.rank( pos + 1 ) - 1;
		final long pioneer = openingPioneers.select( pioneerIndex );
		final long match = pioneer + openingPioneerMatches.getLong( pioneerIndex );

		if ( pos == pioneer ) {
			if ( DEBUG ) System.err.println( "Returning exact pioneer match: " + match );
			return match;
		}
		
		if ( DEBUG ) System.err.println( "pioneer: " + pioneer + "; match: " + match );
		int dist = (int)( pos - pioneer );
		
		if ( ASSERTS ) assert word == pioneer / Long.SIZE : "pos: " + pos + " word:" + word + " pioneer: " + pioneer + " word:" + pioneer / Long.SIZE;
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

}
