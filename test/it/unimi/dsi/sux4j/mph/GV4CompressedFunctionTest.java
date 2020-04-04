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

public class GV4CompressedFunctionTest {

	private void check(final int size, final String[] s, final GV4CompressedFunction<CharSequence> function, final long[] value) {
		for (int i = size; i-- != 0;) assertEquals("globalSeed " + function.globalSeed + " i = " + i, value[i], function.getLong(s[i]));
		function.defaultReturnValue(-1);
		for (int i = 0; i < 100; i++) function.getLong("DEAD" + size + i);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGammaValues() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 0, 1, 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
			final long[] values = new long[size];
			GV3CompressedFunctionTest.generateGamma(values);
			GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(new Codec.Huffman(20)).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, function, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(function, temp);
			function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);
			check(size, s, function, values);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testUniformNumbers() throws IOException, ClassNotFoundException {
		// TODO: restore working codec for size 1
		for (final int maxLength : new int[] { 2, 3, 4, 8, 16, 32, 64 }) {
			for (final int size : new int[] { 0, 1, 1000, 10000 }) {
				final String[] s = new String[size];

				for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
				final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
				final long[] v = new long[size];
				for (int i = 0; i < size; i++) v[i] = r.nextInt(maxLength);
				final Codec codec = new Codec.Huffman();
				GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(codec).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(v)).build();
				check(size, s, function, v);
				final File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(function, temp);
				function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);

				check(size, s, function, v);

			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testUniformBinary() throws IOException, ClassNotFoundException {
		// TODO: restore working codec for size 1
		for (final int maxLength : new int[] { 2, 3, 4, 8, 16, 32, 64 }) {
			for (final int size : new int[] { 0, 1, 1000, 10000 }) {
				final String[] s = new String[size];

				for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
				final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
				final long[] v = new long[size];
				for (int i = 0; i < size; i++) v[i] = r.nextInt(maxLength);
				final Codec codec = new Codec.Binary();
				final LongArrayList values = LongArrayList.wrap(v);
				GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(codec).transform(TransformationStrategies.utf16()).values(values).build();
				check(size, s, function, v);
				final File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(function, temp);
				function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);

				check(size, s, function, v);

			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGeometricValuesHuffman() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 0, 1, 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
			final long[] values = new long[size];
			for (int i = 0; i < size; i++) values[i] = Integer.numberOfTrailingZeros(r.nextInt());
			final Codec.Huffman cdc = new Codec.Huffman();
			GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, function, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(function, temp);
			function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);
			check(size, s, function, values);
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGeometricValuesLengthLimitedHuffman() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 0, 1, 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
			final long[] values = new long[size];
			for (int i = 0; i < size; i++) values[i] = Integer.numberOfTrailingZeros(r.nextInt());
			System.err.println(Arrays.toString(values));
			final Codec.Huffman cdc = new Codec.Huffman(5);
			GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, function, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(function, temp);
			function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);
			check(size, s, function, values);
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGeometricValuesUnary() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 0, 1, 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(0);
			final long[] values = new long[size];
			for (int i = 0; i < size; i++) values[i] = Integer.numberOfTrailingZeros(r.nextInt());
			final Codec.Unary cdc = new Codec.Unary();
			GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, function, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(function, temp);
			function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);
			check(size, s, function, values);
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testZipfianValuesLengthLimitedHuffman() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 0, 1, 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
			final long[] values = new long[size];
			final ZipfDistribution z = new org.apache.commons.math3.distribution.ZipfDistribution(r, 100000, 2);
			for (int i = 0; i < size; i++) values[i] = z.sample();
			final Codec.Huffman cdc = new Codec.Huffman(100);
			GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, function, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(function, temp);
			function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);
			check(size, s, function, values);
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testZipfianValuesGamma() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 0, 1, 100, 1000, 10000 }) {
			final String[] s = new String[size];
			for (int i = s.length; i-- != 0;) s[i] = Integer.toString(i);
			final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
			final long[] values = new long[size];
			final ZipfDistribution z = new org.apache.commons.math3.distribution.ZipfDistribution(r, 100000, 2);
			for (int i = 0; i < size; i++) values[i] = z.sample();
			final Codec.Gamma cdc = new Codec.Gamma();
			GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(s)).codec(cdc).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(values)).build();
			check(size, s, function, values);
			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(function, temp);
			function = (GV4CompressedFunction<CharSequence>)BinIO.loadObject(temp);
			check(size, s, function, values);
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testEmpty() throws IOException, ClassNotFoundException {
		final List<String> emptyList = Collections.emptyList();
		final File temp = File.createTempFile(getClass().getSimpleName(), "test");
		temp.deleteOnExit();

		GV4CompressedFunction<String> function = new GV4CompressedFunction.Builder<String>().keys(emptyList).codec(new Unary()).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, function.getLong("a"));
		BinIO.storeObject(function, temp);
		function = (GV4CompressedFunction<String>)BinIO.loadObject(temp);
		assertEquals(0, function.getLong("a"));

		function = new GV4CompressedFunction.Builder<String>().keys(emptyList).codec(new Unary()).values(LongLists.EMPTY_LIST).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, function.getLong("a"));
		BinIO.storeObject(function, temp);
		function = (GV4CompressedFunction<String>)BinIO.loadObject(temp);
		assertEquals(0, function.getLong("a"));
	}

	@Test
	public void testDuplicates() throws IOException {
		final LongArrayList l = new LongArrayList(new long[] { 1, 2, 0 });
		final GV4CompressedFunction<String> function = new GV4CompressedFunction.Builder<String>().codec(new Codec.Huffman()).values(l).keys(new Iterable<String>() {
			int iteration;

			@Override
			public Iterator<String> iterator() {
				if (iteration++ > 2) return Arrays.asList(new String[] { "a", "b", "c" }).iterator();
				return Arrays.asList(new String[] { "a", "b", "a" }).iterator();
			}
		}).transform(TransformationStrategies.utf16()).build();
		assertEquals(1, function.getLong("a"));
		assertEquals(2, function.getLong("b"));
		assertEquals(0, function.getLong("c"));
	}

	@Test
	public void testLongNumbers() throws IOException {
		final LongArrayList l = new LongArrayList(new long[] { 0x234904309830498L, 0xae049345e9eeeeeL, 0x23445234959234L, 0x239234eaeaeaeL });
		GV4CompressedFunction<CharSequence> function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), function.getLong("a"));
		assertEquals(l.getLong(1), function.getLong("b"));
		assertEquals(l.getLong(2), function.getLong("c"));
		assertEquals(l.getLong(3), function.getLong("d"));
		function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), function.getLong("a"));
		assertEquals(l.getLong(1), function.getLong("b"));
		assertEquals(l.getLong(2), function.getLong("c"));
		assertEquals(l.getLong(3), function.getLong("d"));
		function = new GV4CompressedFunction.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l).indirect().build();
		assertEquals(l.getLong(0), function.getLong("a"));
		assertEquals(l.getLong(1), function.getLong("b"));
		assertEquals(l.getLong(2), function.getLong("c"));
		assertEquals(l.getLong(3), function.getLong("d"));
	}

}
