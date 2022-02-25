/**
 * Succinct data structures for collections.
 *
 * <p>
 * This package provides implementations of some succinct techniques for the storage of static
 * lists. The main ingredient is the Elias&ndash;Fano representation of monotone sequences. For
 * monotone sequences, such as file pointers, an
 * {@link it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList} is the obvious choice. For general
 * sequences, you can either use an {@link it.unimi.dsi.sux4j.util.EliasFanoPrefixSumLongBigList},
 * which stores the sequence using its prefix sums, or an
 * {@link it.unimi.dsi.sux4j.util.EliasFanoLongBigList}. The former is faster and provides also
 * prefix sums, but the latter provides a better compression ratio if the values stored are skewed
 * towards small values. {@link it.unimi.dsi.sux4j.util.EliasFanoIndexedMonotoneLongBigList}
 * provides {@linkplain it.unimi.dsi.sux4j.util.EliasFanoIndexedMonotoneLongBigList#successor(long)
 * content-based addressing methods}.
 *
 * <p>
 * {@link it.unimi.dsi.sux4j.util.MappedEliasFanoMonotoneLongBigList} is a memory-mapped version of
 * {@link it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList}.
 */
package it.unimi.dsi.sux4j.util;