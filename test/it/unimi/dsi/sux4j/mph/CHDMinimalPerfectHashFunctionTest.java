package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.sux4j.mph.CHDMinimalPerfectHashFunction.Builder;

public class CHDMinimalPerfectHashFunctionTest {

	private void check(int size, String[] s, CHDMinimalPerfectHashFunction<CharSequence> mph, int w) {
		final int[] check = new int[s.length];
		Arrays.fill(check, -1);
		for (int i = s.length; i-- != 0;) {
			assertEquals(Integer.toString(i), -1, check[(int)mph.getLong(s[i])]);
			check[(int)mph.getLong(s[i])] = i;
		}

		// Exercise code for negative results
		for (int i = 1000; i-- != 0;)
			if (w != 0) assertEquals(-1, mph.getLong(Integer.toString(i + size)));
			else mph.getLong(Integer.toString(i + size));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {

		for (final int size : new int[] { 0, 1, 4, 8, 20, 64, 100, 1000, 10000, 100000, 1000000 }) {
			for(final int signatureWidth: new int[] { 0, 32, 64 }) {
				System.err.println("Size: " + size  + " w: " + signatureWidth);
				final String[] s = new String[size];
				for (int i = s.length; i-- != 0;)
					s[i] = Integer.toString(i);

				CHDMinimalPerfectHashFunction<CharSequence> mph = new Builder<CharSequence>().keys(Arrays.asList(s)).transform(TransformationStrategies.utf16()).signed(signatureWidth).build();

				check(size, s, mph, signatureWidth);

				final File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (CHDMinimalPerfectHashFunction<CharSequence>)BinIO.loadObject(temp);

				check(size, s, mph, signatureWidth);

				// From store
				final ChunkedHashStore<CharSequence> chunkedHashStore = new ChunkedHashStore<>(TransformationStrategies.utf16(), null, signatureWidth < 0 ? -signatureWidth : 0, null);
				chunkedHashStore.addAll(Arrays.asList(s).iterator());
				chunkedHashStore.checkAndRetry(Arrays.asList(s));
				mph = new CHDMinimalPerfectHashFunction.Builder<CharSequence>().store(chunkedHashStore).signed(signatureWidth).build();
				chunkedHashStore.close();

				check(size, s, mph, signatureWidth);
			}
		}
	}

	@Test
	public void testEmpty() throws IOException {
		final List<String> emptyList = Collections.emptyList();
		final CHDMinimalPerfectHashFunction<String> mph = new CHDMinimalPerfectHashFunction.Builder<String>().keys(emptyList).transform(TransformationStrategies.utf16()).build();
		assertEquals(-1, mph.getLong("a"));
	}
}
