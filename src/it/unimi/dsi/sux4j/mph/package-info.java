/**
 * <p>Static [[monotone] minimal perfect hash] functions.
 *
 * <h2>Static functions</h2>
 *
 * <p>This package provides a number of state-of-the-art implementations of <em>static functions</em>, that is, immutable
 * structures that map a set of objects to a set of values. In particular, we provide <em>minimal perfect hash functions</em> (hence the package name),
 * which map <var>n</var> objects to the first <var>n</var> natural numbers bijectively, and <em>monotone
 * minimal perfect hash functions</em>, which map <var>n</var> objects to their lexicographical rank (i.e., the bijective mapping
 * respects lexicographical order). All structures can compute their result using a space storage that is way below the information-theoretical
 * lower bound for a dictionary because they have an unpredictable result when queried with keys outside of the original set.
 * You can sometimes control the behaviour outside of the original set, however, using <em>signatures</em> (read below).
 * In particular, some of the maps can actually be used to store an <em>approximate dictionary</em> in a space very close to the optimum.
 *
 * <p>Most of the functions have constant lookup time, except for the computation of a hash function on the key.
 *
 * <h2>Usage</h2>
 *
 * <p>Most functions in this package provide a {@linkplain it.unimi.dsi.sux4j.mph.GOV3Function.Builder builder}, rather than a constructor,
 * and feature an easy-to-use main method to build serialized functions from the command line.
 * Moreover, they implement the {@link it.unimi.dsi.fastutil.objects.Object2LongFunction Object2LongFunction} interface,
 * but the underlying machinery manipulates {@linkplain it.unimi.dsi.bits.BitVector bit vectors} only. To bring you own data
 * into the bit-vector world, each function builder
 * requires to specify a {@linkplain it.unimi.dsi.bits.TransformationStrategy transformation strategy}
 * that maps your objects into bit vectors. Many ready-made strategies can be found in {@link it.unimi.dsi.bits.TransformationStrategies TransformationStrategies}:
 * for example,
 * {@link it.unimi.dsi.bits.TransformationStrategies#rawUtf16() rawUtf16()}, {@link it.unimi.dsi.bits.TransformationStrategies#utf16() utf16()} and
 * {@link it.unimi.dsi.bits.TransformationStrategies#prefixFreeUtf16() prefixFreeUtf16()} can be used with character sequences,
 * whereas
 * {@link it.unimi.dsi.bits.TransformationStrategies#rawIso() rawIso()}, {@link it.unimi.dsi.bits.TransformationStrategies#iso() iso()} and
 * {@link it.unimi.dsi.bits.TransformationStrategies#prefixFreeIso() prefixFreeIso()} can be used with character sequences
 * that are known to be within the range of the ISO-8859-1 charset.
 *
 * <p>Note that if you plan to use monotone hashing, you must provide objects in an order such that the corresponding bit vectors
 * are lexicographically ordered. For instance, {@link it.unimi.dsi.bits.TransformationStrategies#utf16() utf16()} obtain this
 * results by concatenating the <em>reversed</em> 16-bit representation of each character. Sometimes, you need also
 * the resulting bit vectors to be {@linkplain it.unimi.dsi.bits.TransformationStrategies#prefixFreeUtf16()  prefix-free}.
 * If order is not an issue,
 * <em>{@linkplain it.unimi.dsi.bits.TransformationStrategies raw}</em> transformation strategies do not reverse bit order, so
 * they are usually faster.
 *
 * <p>For maps whose size depends on the length of the bit vectors being hashed,
 * the {@link it.unimi.dsi.bits.HuTuckerTransformationStrategy HuTuckerTransformationStrategy}
 * provides lexicographical optimal compression, at the price of a slower lookup.
 *
 * <h2>Signing functions</h2>
 *
 * <p>All functions in this package will return a value in their range for most of the keys that are not in their domain.
 * In the few cases in which it is possible to detect a key out of the original set, you will get the {@linkplain
 * it.unimi.dsi.fastutil.objects.Object2LongFunction#defaultReturnValue() default return value}.
 *
 * <p>If you are interested in getting a more precise behaviour, you can <em>sign</em> a function, that is, you can record
 * a signature for each key and use it to filter keys out of the original set. For that to happen, the function must be a
 * bijection of the original set with the first natural numbers: this happens with (monotone) minimal perfect hash functions
 * and with suitable static functions.
 * Several classes provide built-in signing (look at the methods of their builder).
 *
 * <p>An interesting application of the signing technique makes it possible to store a set of objects with a small
 * probability of error (false positives): one simply stores a function mapping each object to its signature. At lookup
 * time, the signature of the object is compared with the output of the function. For instance, using a {@link it.unimi.dsi.sux4j.mph.GOV4Function GOV4Function}
 * one can store a dictionary with probability of a false positive 2<sup><var>&minus;w</var></sup> using just 1.03<var>nw</var>
 * bits, that is, just 3% more than the theoretical lower bound. Look for the {@link it.unimi.dsi.sux4j.mph.GOV4Function.Builder#dictionary(int) dictionary()}
 * method in builders.
 *
 * <p>There are also external signing classes for character sequences provided
 * by the <a href="http://dsiutils.dsi.unimi.it/">DSI utilities</a> which actually implement the interface
 * {@link it.unimi.dsi.util.StringMap StringMap}; in particular, {@link it.unimi.dsi.util.LiterallySignedStringMap LiterallySignedStringMap}
 * will provide a full {@link it.unimi.dsi.util.StringMap StringMap} implementation.
 *
 * <h2>Available data structures</h2>
 *
 * The classes can be gathered in three broad groups:
 * <ul>
 * <li><strong>General functions</strong>. They can be used to associate arbitrary values to a set of objects,
 * so, in particular, they can be used to implement <em>order-preserving minimal perfect hashing</em> (elements are mapped
 * to their order in which they were provided, independently of their lexicographical order). They are also essential
 * building blocks for all other classes. Out of this class we suggest as a workhorse {@link it.unimi.dsi.sux4j.mph.GOV3Function},
 * {@link it.unimi.dsi.sux4j.mph.GOV4Function} (slightly slower, smaller space) and {@link it.unimi.dsi.sux4j.mph.TwoStepsGOV3Function}
 * (slower, but uses less space if the distribution of the output values is skewed, i.e., some values are very frequent).
 *
 * <li><strong>Compressed functions</strong>. As above, but static functions of this type
 * are targeted at functions whose output is not evenly
 * distributed (i.e., some values are much more frequent than others).
 * The space usage per key is proportional to the empirical 0-th order entropy of the output values,
 * rather than to the number of bits that are necessary to express the larger value.
 * Out of this class we suggest as a workhorse {@link it.unimi.dsi.sux4j.mph.GV3CompressedFunction}.
 *
 * <li><strong>Minimal perfect hash function</strong>; they map a set
 * of <var>n</var> object bijectively to the set <var>n</var> = { 0, 1,&hellip; <var>n</var> &minus; 1 }.
 * Out of this class we suggest as a workhorse {@link it.unimi.dsi.sux4j.mph.GOVMinimalPerfectHashFunction},
 * which uses &#8776;2.24 bits per key and has very fast lookups.
 *
 * <li><strong><em>Monotone</em> minimal perfect hash functions</strong>; these
 * functions requires keys to be prefix-free and provided in lexicographical order. They will map back each key to its position using
 * a very small number of bits per element, providing different space/time tradeoffs (in what follows,
 * &#x2113; is the maximum string length):
 * <ul>
 * <li>{@link it.unimi.dsi.sux4j.mph.LcpMonotoneMinimalPerfectHashFunction LcpMonotoneMinimalPerfectHashFunction} is very fast, as it has just to evaluate three {@link it.unimi.dsi.sux4j.mph.GOV3Function GOV3Function}s
 * (so if the length of the strings is a constant multiplied by the machine word, it is actually constant time); however it uses
 * &#8776;2 + log log <var>n</var> + log &#x2113; bits per element.
 * <li>{@link it.unimi.dsi.sux4j.mph.TwoStepsLcpMonotoneMinimalPerfectHashFunction TwoStepsLcpMonotoneMinimalPerfectHashFunction} gains
 * a few bits by performing some additional compression, but it is usually slightly slower (albeit always constant time).
 * <li>{@link it.unimi.dsi.sux4j.mph.ZFastTrieDistributorMonotoneMinimalPerfectHashFunction ZFastTrieDistributorMonotoneMinimalPerfectHashFunction}
 * uses very little space: in fact, about one byte per key independently of the key set size (and basically of the bit
 * vector length). It is an order of magnitude slower than an {@link it.unimi.dsi.sux4j.mph.LcpMonotoneMinimalPerfectHashFunction LcpMonotoneMinimalPerfectHashFunction}.
 * </ul>
 * </ul>
 *
 * <p>{@link it.unimi.dsi.sux4j.mph.LcpMonotoneMinimalPerfectHashFunction} and {@link it.unimi.dsi.sux4j.mph.ZFastTrieDistributorMonotoneMinimalPerfectHashFunction}
 * were introduced by Djamal Belazzougui, Paolo Boldi, Rasmus Pagh and Sebastiano Vigna
 * in &ldquo;Monotone Minimal Perfect Hashing: Searching a Sorted Table with <i>O</i>(1) Accesses&rdquo;,
 * Proc. of the 20th Annual ACM&ndash;SIAM Symposium On Discrete Mathematics (SODA), ACM Press, 2009.
 * {@link it.unimi.dsi.sux4j.mph.TwoStepsLcpMonotoneMinimalPerfectHashFunction} was introduced
 *  by the same authors in &ldquo;Theory and practice of monotone minimal perfect hashing&rdquo;, <i>ACM Journal of Experimental Algorithmics</i>,
 *  16(3):132&minus;144, 2011.
 *
 * <h2>Additional data structures</h2>
 *
 * <ul>
 * <li><strong>General functions</strong>; {@link  it.unimi.dsi.sux4j.mph.MWHCFunction MWHCFunction} and {@link it.unimi.dsi.sux4j.mph.TwoStepsMWHCFunction TwoStepsMWHCFunction}
 * are deprecated: they are still around for historical interest and for backward compatibility, as {@link it.unimi.dsi.sux4j.mph.GOV3Function GOV3Function}, {@link it.unimi.dsi.sux4j.mph.GOV4Function GOV4Function} and
 * {@link it.unimi.dsi.sux4j.mph.TwoStepsGOV3Function TwoStepsGOV3Function} beat them under every respect (except for a slightly greater construction time).
 *
 * <li><strong>Minimal perfect hash function</strong>; {@link it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction MinimalPerfectHashFunction}
 * is deprecated: it is still around for historical interest and for backward compatibility, as {@link it.unimi.dsi.sux4j.mph.GOVMinimalPerfectHashFunction GOVMinimalPerfectHashFunction}
 * beats it under every respect (except for a slightly greater construction time). {@link it.unimi.dsi.sux4j.mph.CHDMinimalPerfectHashFunction CHDMinimalPerfectHashFunction}
 * is theoretically interesting as it can reach almost 2 bits per keys, with a 10% space gain with respect to
 * {@link it.unimi.dsi.sux4j.mph.GOVMinimalPerfectHashFunction GOVMinimalPerfectHashFunction}, but at the cost of slower
 * lookups and a construction time that is an order of magnitude slower.
 *
 * <li><strong><em>Monotone</em> minimal perfect hash functions</strong>;
 * <ul>
 * <li>{@link it.unimi.dsi.sux4j.mph.PaCoTrieDistributorMonotoneMinimalPerfectHashFunction PaCoTrieDistributorMonotoneMinimalPerfectHashFunction} is very slow, as it uses a <em>{@linkplain it.unimi.dsi.sux4j.mph.PaCoTrieDistributor partial compacted trie}</em>
 * (which requires linear time to be accessed) to distribute keys between buckets; theoretically it uses
 * &#8776;2 + log(&#x2113; - log <var>n</var>)  bits per element, but the partial compacted trie is every efficiency in exploiting data redundancy, so the actual
 * occupancy is in general half with respect to the previous function.</li>
 * <li>{@link it.unimi.dsi.sux4j.mph.HollowTrieMonotoneMinimalPerfectHashFunction HollowTrieMonotoneMinimalPerfectHashFunction} is <em>rather</em> slow as it has to traverse a succinct trie on the whole key set;
 * it uses just 4 + log &#x2113; + log log &#x2113; bits per element, and in practice it
 * is the monotone minimal perfect hash function that uses less space.</li>
 * <li>{@link it.unimi.dsi.sux4j.mph.HollowTrieDistributorMonotoneMinimalPerfectHashFunction HollowTrieDistributorMonotoneMinimalPerfectHashFunction} is <em>very</em> slow, as it
 * uses a {@linkplain it.unimi.dsi.sux4j.mph.HollowTrieMonotoneMinimalPerfectHashFunction enriched hollow trie as a distributor}, but it
 * has the (quite surprising) property of using 3.05 + 1.03 log log &#x2113; bits per element (note the double log). In practice,
 * it will use less than a byte per element for strings of length up to a billion bits.</li>
 * <li>Variable-length versions (e.g., {@link it.unimi.dsi.sux4j.mph.VLLcpMonotoneMinimalPerfectHashFunction VLLcpMonotoneMinimalPerfectHashFunction} and
 * {@link it.unimi.dsi.sux4j.mph.VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction}) are structures whose size depends
 * on the <em>average</em>, rather than on the <em>maximum</em> string length, but they are mainly of theoretical interest.
 * </ul>
 * </ul>
 * <p>{@link it.unimi.dsi.sux4j.mph.PaCoTrieDistributorMonotoneMinimalPerfectHashFunction},
 *  {@link it.unimi.dsi.sux4j.mph.HollowTrieMonotoneMinimalPerfectHashFunction} and {@link it.unimi.dsi.sux4j.mph.HollowTrieDistributorMonotoneMinimalPerfectHashFunction} were introduced
 *  by Djamal Belazzougui, Paolo Boldi, Rasmus Pagh and Sebastiano Vigna in &ldquo;Theory and practice of monotone minimal perfect hashing&rdquo;, <i>ACM Journal of Experimental Algorithmics</i>,
 *  16(3):132&minus;144, 2011.
 */

package it.unimi.dsi.sux4j.mph;
