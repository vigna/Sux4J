package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.Fast;
import junit.framework.TestCase;

public class FastTest extends TestCase {

	
	public void testCeilLog2() {
		for( int i = 1; i < 1000; i++ ) assertEquals( (int)Math.ceil( Math.log( i ) / Math.log( 2 ) ), Fast.ceilLog2( i ) );
	}
}
