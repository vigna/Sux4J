/*
 * SpookyHash - 128-bit noncryptographic hash function
 *
 * Written in 2012 by Bob Jenkins
 *
 * Converted to C in 2015 by Joergen Ibsen
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty. <http://creativecommons.org/publicdomain/zero/1.0/>
 *
 * Original comment from SpookyV2.h by Bob Jenkins:
 *
 * SpookyHash: a 128-bit noncryptographic hash function
 * By Bob Jenkins, public domain
 *   Oct 31 2010: alpha, framework + SpookyHash::Mix appears right
 *   Oct 31 2011: alpha again, Mix only good to 2^^69 but rest appears right
 *   Dec 31 2011: beta, improved Mix, tested it for 2-bit deltas
 *   Feb  2 2012: production, same bits as beta
 *   Feb  5 2012: adjusted definitions of uint* to be more portable
 *   Mar 30 2012: 3 bytes/cycle, not 4.  Alpha was 4 but wasn't thorough enough.
 *   August 5 2012: SpookyV2 (different results)
 * 
 * Up to 3 bytes/cycle for long messages.  Reasonably fast for short messages.
 * All 1 or 2 bit deltas achieve avalanche within 1% bias per output bit.
 *
 * This was developed for and tested on 64-bit x86-compatible processors.
 * It assumes the processor is little-endian.  There is a macro
 * controlling whether unaligned reads are allowed (by default they are).
 * This should be an equally good hash on big-endian machines, but it will
 * compute different results on them than on little-endian machines.
 *
 * Google's CityHash has similar specs to SpookyHash, and CityHash is faster
 * on new Intel boxes.  MD4 and MD5 also have similar specs, but they are orders
 * of magnitude slower.  CRCs are two or more times slower, but unlike 
 * SpookyHash, they have nice math for combining the CRCs of pieces to form 
 * the CRCs of wholes.  There are also cryptographic hashes, but those are even 
 * slower than MD5.
 */

#ifndef SPOOKY_H_INCLUDED
#define SPOOKY_H_INCLUDED

#include <stddef.h>
#include <stdint.h>

// size of the internal state
#define SC_BLOCKSIZE (SC_NUMVARS * 8U)

// size of buffer of unhashed data, in bytes
#define SC_BUFSIZE (2U * SC_BLOCKSIZE)

void spooky_short(const void *restrict message, size_t length, uint64_t seed, uint64_t *tuple);
void spooky_short_rehash(const uint64_t *signature, const uint64_t seed, uint64_t * const tuple);

#endif /* SPOOKY_H_INCLUDED */
