package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.sux4j.io.BucketedHashStore;

public class GOV4FunctionTest {


	private void check(final int size, final String[] s, final GOV4Function<CharSequence> function, final int signatureWidth) {
		if (signatureWidth < 0) for (int i = s.length; i-- != 0;) assertEquals(1, function.getLong(s[i]));
		else for (int i = s.length; i-- != 0;) assertEquals(i, function.getLong(s[i]));

		// Exercise code for negative results
		if (signatureWidth == 0) for (int i = size; i-- != 0;) function.getLong(Integer.toString(i + size));
		else if (signatureWidth < 0) for (int i = size; i-- != 0;) assertEquals(0, function.getLong(Integer.toString(i + size)));
		else for (int i = size; i-- != 0;) assertEquals(-1, function.getLong(Integer.toString(i + size)));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {
		for (int outputWidth = 20; outputWidth < Long.SIZE; outputWidth += 8) {
			for (final int signatureWidth: new int[] { -32, 0, 32, 64 }) {
				for (final int size : new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 64, 95, 96, 97, 98, 99, 100, 101, 1000, 10000, 100000 }) {
					final String[] s = new String[size];
					for (int i = s.length; i-- != 0;)
						s[i] = Integer.toString(i);

					GOV4Function<CharSequence> function = new GOV4Function.Builder<CharSequence>().keys(Arrays.asList(s)).transform(TransformationStrategies.utf16()).signed(signatureWidth).build();

					check(size, s, function, signatureWidth);

					final File temp = File.createTempFile(getClass().getSimpleName(), "test");
					temp.deleteOnExit();
					BinIO.storeObject(function, temp);
					function = (GOV4Function<CharSequence>)BinIO.loadObject(temp);

					check(size, s, function, signatureWidth);

					// From store
					final BucketedHashStore<CharSequence> bucketedHashStore = new BucketedHashStore<>(TransformationStrategies.utf16(), null, signatureWidth < 0 ? -signatureWidth : 0, null);
					bucketedHashStore.addAll(Arrays.asList(s).iterator());
					bucketedHashStore.checkAndRetry(Arrays.asList(s));
					function = new GOV4Function.Builder<CharSequence>().store(bucketedHashStore).signed(signatureWidth).build();
					bucketedHashStore.close();

					check(size, s, function, signatureWidth);
				}
			}
		}
	}

	@Test
	public void testLongNumbers() throws IOException {
		final LongArrayList l = new LongArrayList(new long[] { 0x234904309830498L, 0xae049345e9eeeeeL, 0x23445234959234L, 0x239234eaeaeaeL });
		GOV4Function<CharSequence> function = new GOV4Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), function.getLong("a"));
		assertEquals(l.getLong(1), function.getLong("b"));
		assertEquals(l.getLong(2), function.getLong("c"));
		assertEquals(l.getLong(3), function.getLong("d"));
		function = new GOV4Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l, Long.SIZE).build();
		assertEquals(l.getLong(0), function.getLong("a"));
		assertEquals(l.getLong(1), function.getLong("b"));
		assertEquals(l.getLong(2), function.getLong("c"));
		assertEquals(l.getLong(3), function.getLong("d"));
		function = new GOV4Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l, Long.SIZE).indirect().build();
		assertEquals(l.getLong(0), function.getLong("a"));
		assertEquals(l.getLong(1), function.getLong("b"));
		assertEquals(l.getLong(2), function.getLong("c"));
		assertEquals(l.getLong(3), function.getLong("d"));
	}

	@Test
	public void testDictionary() throws IOException {
		final GOV4Function<CharSequence> mph = new GOV4Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).dictionary(8).build();
		assertEquals(1, mph.getLong("a"));
		assertEquals(1, mph.getLong("b"));
		assertEquals(1, mph.getLong("c"));
		assertEquals(1, mph.getLong("d"));
		assertEquals(0, mph.getLong("e"));
	}

	@Test
	public void testDuplicates() throws IOException {
		final GOV4Function<String> mph = new GOV4Function.Builder<String>().keys(
				new Iterable<String>() {
					int iteration;

					@Override
					public Iterator<String> iterator() {
						if (iteration++ > 2) return Arrays.asList(new String[] { "a", "b", "c" }).iterator();
						return Arrays.asList(new String[] { "a", "b", "a" }).iterator();
					}
				}).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, mph.getLong("a"));
		assertEquals(1, mph.getLong("b"));
		assertEquals(2, mph.getLong("c"));
	}

	@Test
	public void testEmpty() throws IOException {
		final List<String> emptyList = Collections.emptyList();
		GOV4Function<String> mph = new GOV4Function.Builder<String>().keys(emptyList).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, mph.getLong("a"));
		mph = new GOV4Function.Builder<String>().keys(emptyList).dictionary(10).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, mph.getLong("a"));
		mph = new GOV4Function.Builder<String>().keys(emptyList).values(LongLists.EMPTY_LIST, 10).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, mph.getLong("a"));

	}
}
