package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.fastutil.longs.LongList;

public interface LongBigList extends LongList {
	public long getLong( long index );
	public long set( long index, long value );
	public void add( long index, long value );
	public long length();
}
