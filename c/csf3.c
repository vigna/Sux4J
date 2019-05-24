/*
 * Sux: Succinct data structures
 *
 * Copyright (C) 2018 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <math.h>
#include "csf3.h"
#include "spooky.h"

static uint64_t inline decode(const csf * const csf, const uint64_t value) {	
	for (int curr = 0;; curr++)
		if (value < csf->last_codeword_plus_one[curr]) {
			const int s = csf->shift[curr];
			return csf->symbol[(value >> s) - (csf->last_codeword_plus_one[curr] >> s) + csf->how_many_up_to_block[curr]];
		}
}

static void inline signature_to_equation(const uint64_t *signature, const uint64_t seed, int num_variables, int *e) {
	uint64_t hash[4];
	spooky_short_rehash(signature, seed, hash);
	const int shift = __builtin_clzll(num_variables);
	const uint64_t mask = (UINT64_C(1) << shift) - 1;
	e[0] = ((hash[0] & mask) * num_variables) >> shift;
	e[1] = ((hash[1] & mask) * num_variables) >> shift;
	e[2] = ((hash[2] & mask) * num_variables) >> shift;
}
																										

#define OFFSET_MASK (UINT64_C(-1) >> 10)

static uint64_t inline get_value(const uint64_t * const array, uint64_t pos, const int width) {
	const int l = 64 - width;
	const int start_word = pos / 64;
	const int start_bit = pos % 64;
	if (start_bit <= l) return array[start_word] << l - start_bit >> l;
	return array[start_word] >> start_bit | array[start_word + 1] << 64 + l - start_bit >> l;
}

int64_t csf3_get_byte_array(const csf *csf, char *key, uint64_t len) {
	uint64_t signature[4];
	spooky_short(key, len, csf->global_seed, signature);
	const int bucket = ((__uint128_t)(signature[0] >> 1) * (__uint128_t)csf->multiplier) >> 64;
	const uint64_t offset_seed = csf->offset_and_seed[bucket];
	const uint64_t bucket_offset = offset_seed & OFFSET_MASK;
	const int w = csf->global_max_codeword_length;
	const int num_variables = (csf->offset_and_seed[bucket + 1] & OFFSET_MASK) - bucket_offset - w;
	int e[3];
	signature_to_equation(signature, offset_seed & ~OFFSET_MASK, num_variables, e);
	const int64_t t = decode(csf, get_value(csf->array, e[0] + bucket_offset, w) ^ get_value(csf->array, e[1] + bucket_offset, w) ^ get_value(csf->array, e[2] + bucket_offset, w));
	if (__builtin_expect(t != -1, 1)) return t;
	const uint64_t end = csf->global_max_codeword_length - csf->escape_length;
	const uint64_t start = end - csf->escaped_symbol_length;
	return get_value(csf->array, e[0] + start, e[0] + end) ^ get_value(csf->array, e[1] + start, e[1] + end) ^ get_value(csf->array, e[2] + start, e[2] + end);
}

int64_t csf3_get_uint64_t(const csf *csf, const uint64_t key) {
	uint64_t signature[4];
	spooky_short(&key, 8, csf->global_seed, signature);
	const int bucket = ((__uint128_t)(signature[0] >> 1) * (__uint128_t)csf->multiplier) >> 64;
	const uint64_t offset_seed = csf->offset_and_seed[bucket];
	const uint64_t bucket_offset = offset_seed & OFFSET_MASK;
	const int w = csf->global_max_codeword_length;
	const int num_variables = (csf->offset_and_seed[bucket + 1] & OFFSET_MASK) - bucket_offset - w;
	int e[3];
	signature_to_equation(signature, offset_seed & ~OFFSET_MASK, num_variables, e);
	const int64_t t = decode(csf, get_value(csf->array, e[0] + bucket_offset, w) ^ get_value(csf->array, e[1] + bucket_offset, w) ^ get_value(csf->array, e[2] + bucket_offset, w));
	if (__builtin_expect(t != -1, 1)) return t;
	const uint64_t end = csf->global_max_codeword_length - csf->escape_length;
	const uint64_t start = end - csf->escaped_symbol_length;
	return get_value(csf->array, e[0] + start, e[0] + end) ^ get_value(csf->array, e[1] + start, e[1] + end) ^ get_value(csf->array, e[2] + start, e[2] + end);
}
