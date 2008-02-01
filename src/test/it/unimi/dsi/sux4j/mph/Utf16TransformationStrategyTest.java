package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.sux4j.mph.Utf16TransformationStrategy;
import junit.framework.TestCase;

public class Utf16TransformationStrategyTest extends TestCase {

	public void testGetLong() {
		Utf16TransformationStrategy utfStrategy = new Utf16TransformationStrategy();
		String s = new String( new char[] { '\u0001', '\u0002' } );
		assertEquals( 0x40008000L, utfStrategy.toBitVector( s ).getLong( 0, 32 ) );
		assertEquals( 0x40008000L, utfStrategy.toBitVector( s ).getLong( 0, 48 ) );
		s = new String( new char[] { '\u0001', '\u0002', '\u0003' } );
		assertEquals( 0xC00040008000L, utfStrategy.toBitVector( s ).getLong( 0, 48 ) );
		assertEquals( 0xC00040008000L, utfStrategy.toBitVector( s ).getLong( 0, 64 ) );
		s = new String( new char[] { '\u0001', '\u0002', '\u0003', '\u0004' } );
		assertEquals( 0x2000C00040008000L, utfStrategy.toBitVector( s ).getLong( 0, 64 ) );
		assertEquals( 0, utfStrategy.toBitVector( s ).getLong( 64, 80 ) );
		//System.err.println( Long.toHexString( utfStrategy.toBitVector( s ).getLong( 16, 80 ) ));
		assertEquals( 0x2000C0004000L, utfStrategy.toBitVector( s ).getLong( 16, 80 ) );
	}
	
}
