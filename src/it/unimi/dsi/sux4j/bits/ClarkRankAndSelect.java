package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.mg4j.util.Fast;

public class ClarkRankAndSelect implements RankAndSelect {
	final long size;
	final long[] bits;
	final int blockBits;
	final int superBlockBits;
	final long[] blocks;
	final long[] superBlocks;
	
	public static long ceilLog2( final long x ) {
		return Fast.mostSignificantBit( x - 1 ) + 1;
	}
	
	public ClarkRankAndSelect( long[] bits, long size ) {
		this.bits = bits;
		this.size = size;
		this.blockBits = (int)( ceilLog2( size ) / 2 );
		this.superBlockBits = (int)( blockBits * ceilLog2( size ) );
		throw new UnsupportedOperationException();
	}
	
	
	public long rank( long pos ) {
		// TODO Auto-generated method stub
		return 0;
	}

	public long select( long rank ) {
		throw new UnsupportedOperationException();
	}

	public long size() {
		return size;
	}

}
