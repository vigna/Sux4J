//
// SpookyHash - 128-bit noncryptographic hash function
//
// Written in 2012 by Bob Jenkins
//
// Converted to C in 2015 by Joergen Ibsen
//
// To the extent possible under law, the author(s) have dedicated all
// copyright and related and neighboring rights to this software to the
// public domain worldwide. This software is distributed without any
// warranty. <http://creativecommons.org/publicdomain/zero/1.0/>
//
// Original comment from SpookyV2.cpp by Bob Jenkins:
//
// Spooky Hash
// A 128-bit noncryptographic hash, for checksums and table lookup
// By Bob Jenkins.  Public domain.
//   Oct 31 2010: published framework, disclaimer ShortHash isn't right
//   Nov 7 2010: disabled ShortHash
//   Oct 31 2011: replace End, ShortMix, ShortEnd, enable ShortHash again
//   April 10 2012: buffer overflow on platforms without unaligned reads
//   July 12 2012: was passing out variables in final to in/out in short
//   July 30 2012: I reintroduced the buffer overflow
//   August 5 2012: SpookyV2: d = should be d += in short hash, and remove extra mix from long hash

#include "spooky.h"

#include <string.h>
#include <stdbool.h>
#include <stdio.h>

//
// SC_CONST: a constant which:
//  - is not zero
//  - is odd
//  - is a not-very-regular mix of 1's and 0's
//  - does not need any other special mathematical properties
//
#define SC_CONST (0x9e3779b97f4a7c13)

#define ROTL64(x, k) (((x) << (k)) | ((x) >> (64 - (k))))

static inline uint64_t spooky_read_le64(const uint64_t *s) {
		const uint8_t *p = (const uint8_t *) s;
		return (uint64_t) p[0]
		    | ((uint64_t) p[1] << 8)
		    | ((uint64_t) p[2] << 16)
		    | ((uint64_t) p[3] << 24)
		    | ((uint64_t) p[4] << 32)
		    | ((uint64_t) p[5] << 40)
		    | ((uint64_t) p[6] << 48)
		    | ((uint64_t) p[7] << 56);
}

static inline void spooky_short_mix(uint64_t *h) {
	h[2] = ROTL64(h[2], 50);  h[2] += h[3];  h[0] ^= h[2];
	h[3] = ROTL64(h[3], 52);  h[3] += h[0];  h[1] ^= h[3];
	h[0] = ROTL64(h[0], 30);  h[0] += h[1];  h[2] ^= h[0];
	h[1] = ROTL64(h[1], 41);  h[1] += h[2];  h[3] ^= h[1];
	h[2] = ROTL64(h[2], 54);  h[2] += h[3];  h[0] ^= h[2];
	h[3] = ROTL64(h[3], 48);  h[3] += h[0];  h[1] ^= h[3];
	h[0] = ROTL64(h[0], 38);  h[0] += h[1];  h[2] ^= h[0];
	h[1] = ROTL64(h[1], 37);  h[1] += h[2];  h[3] ^= h[1];
	h[2] = ROTL64(h[2], 62);  h[2] += h[3];  h[0] ^= h[2];
	h[3] = ROTL64(h[3], 34);  h[3] += h[0];  h[1] ^= h[3];
	h[0] = ROTL64(h[0], 5);   h[0] += h[1];  h[2] ^= h[0];
	h[1] = ROTL64(h[1], 36);  h[1] += h[2];  h[3] ^= h[1];
}



static inline void spooky_short_end(uint64_t *h) {
	h[3] ^= h[2];  h[2] = ROTL64(h[2], 15);  h[3] += h[2];
	h[0] ^= h[3];  h[3] = ROTL64(h[3], 52);  h[0] += h[3];
	h[1] ^= h[0];  h[0] = ROTL64(h[0], 26);  h[1] += h[0];
	h[2] ^= h[1];  h[1] = ROTL64(h[1], 51);  h[2] += h[1];
	h[3] ^= h[2];  h[2] = ROTL64(h[2], 28);  h[3] += h[2];
	h[0] ^= h[3];  h[3] = ROTL64(h[3], 9);   h[0] += h[3];
	h[1] ^= h[0];  h[0] = ROTL64(h[0], 47);  h[1] += h[0];
	h[2] ^= h[1];  h[1] = ROTL64(h[1], 54);  h[2] += h[1];
	h[3] ^= h[2];  h[2] = ROTL64(h[2], 32);  h[3] += h[2];
	h[0] ^= h[3];  h[3] = ROTL64(h[3], 25);  h[0] += h[3];
	h[1] ^= h[0];  h[0] = ROTL64(h[0], 63);  h[1] += h[0];
}

void spooky_short_rehash(const uint64_t *triple, const uint64_t seed, uint64_t * const tuple) {
	uint64_t h[4];
	h[0] = seed;
	h[1] = SC_CONST + triple[0];
	h[2] = SC_CONST + triple[1];
	h[3] = SC_CONST + triple[2];
	spooky_short_mix(h);
	memcpy(tuple, h, sizeof h);
}

void spooky_short(const void *restrict message, size_t length, uint64_t seed, uint64_t *tuple) {
	union {
		const uint8_t *p8;
		uint64_t *p64;
	} u;

	u.p8 = (const uint8_t *) message;

	size_t left = length % 32;
	uint64_t h[4];
	h[0] = seed;
	h[1] = seed;
	h[2] = SC_CONST;
	h[3] = SC_CONST;

	if (length > 15) {
		const uint64_t *end = u.p64 + (length / 32) * 4;

		// handle all complete sets of 32 bytes
		for (; u.p64 < end; u.p64 += 4) {
			h[2] += spooky_read_le64(&u.p64[0]);
			h[3] += spooky_read_le64(&u.p64[1]);
			spooky_short_mix(h);
			h[0] += spooky_read_le64(&u.p64[2]);
			h[1] += spooky_read_le64(&u.p64[3]);
		}

		//Handle the case of 16+ remaining bytes.
		if (left >= 16) {
			h[2] += spooky_read_le64(&u.p64[0]);
			h[3] += spooky_read_le64(&u.p64[1]);
			spooky_short_mix(h);
			u.p64 += 2;
			left -= 16;
		}
	}

	// Handle the last 0..15 bytes, and its length
	switch (left) {
	case 15:
		h[3] += ((uint64_t) u.p8[14]) << 48;
	case 14:
		h[3] += ((uint64_t) u.p8[13]) << 40;
	case 13:
		h[3] += ((uint64_t) u.p8[12]) << 32;
	case 12:
		h[3] += ((uint64_t) u.p8[11]) << 24;
	case 11:
		h[3] += ((uint64_t) u.p8[10]) << 16;
	case 10:
		h[3] += ((uint64_t) u.p8[9]) << 8;
	case 9:
		h[3] += (uint64_t) u.p8[8];
	case 8:
		h[2] += spooky_read_le64(&u.p64[0]);
		break;
	case 7:
		h[2] += ((uint64_t) u.p8[6]) << 48;
	case 6:
		h[2] += ((uint64_t) u.p8[5]) << 40;
	case 5:
		h[2] += ((uint64_t) u.p8[4]) << 32;
	case 4:
		h[2] += ((uint64_t) u.p8[3]) << 24;
	case 3:
		h[2] += ((uint64_t) u.p8[2]) << 16;
	case 2:
		h[2] += ((uint64_t) u.p8[1]) << 8;
	case 1:
		h[2] += (uint64_t) u.p8[0];
		break;
	case 0:
		h[2] += SC_CONST;
		h[3] += SC_CONST;
	}

	h[0] += (uint64_t)length * 8;

	spooky_short_end(h);

	memcpy(tuple, h, sizeof h);
}
