package it.unimi.dsi.sux4j.bits;

public abstract class AbstractRankAndSelect implements RankAndSelect {

	public long count() {
		return rank( length() - 1 );
	}
	
	public long lastOne() {
		return select( rank( length() ) );
	}
	
	public long rank( final long from, final long to ) {
		return rank( to ) - rank( from - 1 );
	}

	public long select( final long from, final long rank ) {
		return select( rank + rank( from - 1 ) );
	}
}
