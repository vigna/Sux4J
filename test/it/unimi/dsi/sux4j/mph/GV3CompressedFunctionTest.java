package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.distribution.ZipfDistribution;
import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.sux4j.mph.codec.Codec;
import it.unimi.dsi.sux4j.mph.codec.Codec.Unary;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class GV3CompressedFunctionTest {

	private void check(int size, String[] s, GV3CompressedFunction<CharSequence> mph, long[] value) {
		for (int i = size; i-- != 0;) assertEquals("globalSeed " + mph.globalSeed + " i = " + i, value[i], mph.getLong(s[i]));
		// test for string outside keyset
		mph.defaultReturnValue(-1);
		for (int i = 0; i < 100; i++) mph.getLong("DEAD" + size + i);
	}



	@SuppressWarnings("unchecked")
	@Test
	public void testGammaValues() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
			final long[] values = new long[size];
			generateGamma(values);
			GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(new Codec.Huffman(20)).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, mph, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);
			check(size, s, mph, values);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testUniformNumbers() throws IOException, ClassNotFoundException {
		// TODO: restore working codec for size 1
		for (final int maxLength : new int[] { 2, 3, 4, 8, 16, 32, 64 }) {
			for (final int size : new int[] { 0, 1000, 10000 }) {
				final String[] s = new String[size];

				for (int i = s.length; i-- != 0;)
					s[i] = Integer.toString(i);
				final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
				final long[] v = new long[size];
				for (int i = 0; i < size; i++) v[i] = r.nextInt(maxLength);
				final Codec codec = new Codec.Huffman();
				final LongArrayList values = LongArrayList.wrap(v);
				GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(codec).transform(TransformationStrategies.utf16()).values(values).build();
				check(size, s, mph, v);
				final File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);

				check(size, s, mph, v);

			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testUniformBinary() throws IOException, ClassNotFoundException {
		// TODO: restore working codec for size 1
		for (final int maxLength : new int[] { 2, 3, 4, 8, 16, 32, 64 }) {
			for (final int size : new int[] { 0, 1000, 10000 }) {
				final String[] s = new String[size];

				for (int i = s.length; i-- != 0;)
					s[i] = Integer.toString(i);
				final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
				final long[] v = new long[size];
				for (int i = 0; i < size; i++) v[i] = r.nextInt(maxLength);
				final Codec codec = new Codec.Binary();
				final LongArrayList values = LongArrayList.wrap(v);
				GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(codec).transform(TransformationStrategies.utf16()).values(values).build();
				check(size, s, mph, v);
				final File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);

				check(size, s, mph, v);

			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGeometricValuesHuffman() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;)
				s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
			final long[] values = new long[size];
			for (int i = 0; i < size; i++) values[i] = Integer.numberOfTrailingZeros(r.nextInt());
			final Codec.Huffman cdc = new Codec.Huffman();
			GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, mph, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);
			check(size, s, mph, values);
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGeometricValuesLengthLimitedHuffman() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;)
				s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
			final long[] values = new long[size];
			for (int i = 0; i < size; i++) {
				values[i] = Integer.numberOfTrailingZeros(r.nextInt());
			}
			final Codec.Huffman cdc = new Codec.Huffman(5);
			GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, mph, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);
			check(size, s, mph, values);
		}

	}


	@SuppressWarnings("unchecked")
	@Test
	public void testGeometricValuesUnary() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;)
				s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
			final long[] values = new long[size];
			for (int i = 0; i < size; i++) {
				values[i] = Integer.numberOfTrailingZeros(r.nextInt());
			}
			final Unary cdc = new Codec.Unary();
			GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, mph, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);
			check(size, s, mph, values);
		}

	}


	@SuppressWarnings("unchecked")
	@Test
	public void testZipfianValuesLengthLimitedHuffman() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;)
				s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
			final long[] values = new long[size];
			final ZipfDistribution z = new org.apache.commons.math3.distribution.ZipfDistribution(r, 100000, 2);
			for (int i = 0; i < size; i++) {
				values[i] = z.sample();
			}
			final Codec.Huffman cdc = new Codec.Huffman(6);
			GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, mph, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);
			check(size, s, mph, values);
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testZipfianValuesGamma() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;)
				s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
			final long[] values = new long[size];
			final ZipfDistribution z = new org.apache.commons.math3.distribution.ZipfDistribution(r, 100000, 2);
			for (int i = 0; i < size; i++) {
				values[i] = z.sample();
			}
			final Codec.Gamma cdc = new Codec.Gamma();
			GV3CompressedFunction<CharSequence> mph = new GV3CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, mph, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (GV3CompressedFunction<CharSequence>) BinIO.loadObject(temp);
			check(size, s, mph, values);
		}

	}


	@Test
	public void testEmpty() throws IOException {
		final List<String> emptyList = Collections.emptyList();
		GV3CompressedFunction<String> mph = new GV3CompressedFunction.Builder<String>().keys(emptyList).codec(new Unary()).transform(TransformationStrategies.utf16()).build();
		assertEquals(-1, mph.getLong("a"));
		mph = new GV3CompressedFunction.Builder<String>().keys(emptyList).codec(new Unary()).values(LongLists.EMPTY_LIST).transform(TransformationStrategies.utf16()).build();
		assertEquals(-1, mph.getLong("a"));

	}

	@Test
	public void testDuplicates() throws IOException {
		final LongArrayList l = new LongArrayList(new long[] { 1, 2, 0 });
		final GV3CompressedFunction<String> mph = new GV3CompressedFunction.Builder<String>().codec(new Codec.Huffman()).values(l).keys(new Iterable<String>() {
			int iteration;

			@Override
			public Iterator<String> iterator() {
				if (iteration++ > 2) return Arrays.asList(new String[] { "a", "b", "c" }).iterator();
				return Arrays.asList(new String[] { "a", "b", "a" }).iterator();
			}
		}).transform(TransformationStrategies.utf16()).build();
		assertEquals(1, mph.getLong("a"));
		assertEquals(2, mph.getLong("b"));
		assertEquals(0, mph.getLong("c"));
	}

	public static void generateGamma(long[] values) {
		final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
		for (int i = 0; i < values.length; i++) {
			final long value = 1 << Long.numberOfTrailingZeros(r.nextLong());
			values[i] = (value | r.nextLong(value)) - 1;
		}
	}
}
