package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongBigLists;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class TwoStepsGOV3FunctionTest {

	@Test
	public void testSimpleList() throws IOException {
		LongBigList l = LongBigLists.asBigList(new LongArrayList(new long[] { 4, 4, 4, 0, 1 }));
		TwoStepsGOV3Function<CharSequence> mph = new TwoStepsGOV3Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d", "e" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		assertEquals(l.getLong(4), mph.getLong("e"));
	}

	@Test
	public void testSimpleCompressedList() throws IOException {
		LongBigList l = LongBigLists.asBigList(new LongArrayList(new long[] { 4, 4, 4, 4, 4, 4, 0, 10000 }));
		TwoStepsGOV3Function<CharSequence> mph = new TwoStepsGOV3Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d", "e", "f", "g", "h" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		assertEquals(l.getLong(4), mph.getLong("e"));
		assertEquals(l.getLong(5), mph.getLong("f"));
		assertEquals(l.getLong(6), mph.getLong("g"));
		assertEquals(l.getLong(7), mph.getLong("h"));
	}

	@Test
	public void testCompressedList() throws IOException {
		LongBigList l = LongBigLists.asBigList(new LongArrayList(new long[] { 4, 4, 3, 3, 3, 4, 0, 10000 }));
		TwoStepsGOV3Function<CharSequence> mph = new TwoStepsGOV3Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d", "e", "f", "g", "h" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		assertEquals(l.getLong(4), mph.getLong("e"));
		assertEquals(l.getLong(5), mph.getLong("f"));
		assertEquals(l.getLong(6), mph.getLong("g"));
		assertEquals(l.getLong(7), mph.getLong("h"));
	}
}
