package it.unimi.dsi.sux4j.bits;


public abstract class ClarkRankAndSelect implements RankAndSelect {
	final long size;
	final long[] bits;
	final int blockBits;
	final int superBlockBits;
	final long[] blocks;
	final long[] superBlocks;
	
	
	public ClarkRankAndSelect( long[] bits, long size ) {
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
