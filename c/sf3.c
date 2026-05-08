/*
 * Sux: Succinct data structures
 *
 * Copyright (C) 2018-2020 Sebastiano Vigna
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
#include "sf3.h"
#include "spooky.h"

static void inline signature_to_equation(const uint64_t *signature, const uint64_t seed, int num_variables, unsigned int *e) {
	uint64_t hash[4];
	spooky_short_rehash(signature, seed, hash);
	const int shift = __builtin_clzll(num_variables);
	const uint64_t mask = (UINT64_C(1) << shift) - 1;
	e[0] = ((hash[0] & mask) * num_variables) >> shift;
	e[1] = ((hash[1] & mask) * num_variables) >> shift;
	e[2] = ((hash[2] & mask) * num_variables) >> shift;
}
																										

#define OFFSET_MASK (UINT64_C(-1) >> 8)

static uint64_t inline get_value(const uint64_t * const array, uint64_t pos, const int width) {
	pos *= width;
	const int l = 64 - width;
	const int start_word = pos / 64;
	const int start_bit = pos % 64;
	if (start_bit <= l) return array[start_word] << l - start_bit >> l;
	return array[start_word] >> start_bit | array[start_word + 1] << 64 + l - start_bit >> l;
}

int64_t sf3_get_byte_array(const sf *sf, char *key, uint64_t len) {
	uint64_t signature[4];
	spooky_short(key, len, sf->global_seed, signature);
	const int bucket = ((__uint128_t)(signature[0] >> 1) * (__uint128_t)sf->multiplier) >> 64;
	const uint64_t offset_seed = sf->offset_and_seed[bucket];
	const uint64_t bucket_offset = offset_seed & OFFSET_MASK;
	const int num_variables = (sf->offset_and_seed[bucket + 1] & OFFSET_MASK) - bucket_offset;
	unsigned int e[3];
	signature_to_equation(signature, offset_seed & ~OFFSET_MASK, num_variables, e);
	/* SF_8/SF_16/SF_32: direct typed-pointer access for power-of-2 widths.
	   Requires little-endian byte order (the packed bit array's byte layout
	   must match typed-pointer indexing). */
#ifdef SF_8
	const int64_t result = ((uint8_t *)sf->array)[e[0] + bucket_offset] ^ ((uint8_t *)sf->array)[e[1] + bucket_offset] ^ ((uint8_t *)sf->array)[e[2] + bucket_offset];
#elif defined(SF_16)
	const int64_t result = ((uint16_t *)sf->array)[e[0] + bucket_offset] ^ ((uint16_t *)sf->array)[e[1] + bucket_offset] ^ ((uint16_t *)sf->array)[e[2] + bucket_offset];
#elif defined(SF_32)
	const int64_t result = ((uint32_t *)sf->array)[e[0] + bucket_offset] ^ ((uint32_t *)sf->array)[e[1] + bucket_offset] ^ ((uint32_t *)sf->array)[e[2] + bucket_offset];
#else
	const int64_t result = get_value(sf->array, e[0] + bucket_offset, sf->width) ^ get_value(sf->array, e[1] + bucket_offset, sf->width) ^ get_value(sf->array, e[2] + bucket_offset, sf->width);
#endif
#ifdef FILTER
	return ((result ^ signature[0]) & ((UINT64_C(1) << sf->width) - 1)) == 0;
#else
	return result;
#endif
}

int64_t sf3_get_uint64_t(const sf *sf, const uint64_t key) {
	uint64_t signature[4];
	spooky_short(&key, 8, sf->global_seed, signature);
	const int bucket = ((__uint128_t)(signature[0] >> 1) * (__uint128_t)sf->multiplier) >> 64;
	const uint64_t offset_seed = sf->offset_and_seed[bucket];
	const uint64_t bucket_offset = offset_seed & OFFSET_MASK;
	const int num_variables = (sf->offset_and_seed[bucket + 1] & OFFSET_MASK) - bucket_offset;
	unsigned int e[3];
	signature_to_equation(signature, offset_seed & ~OFFSET_MASK, num_variables, e);
#ifdef SF_8
	const int64_t result = ((uint8_t *)sf->array)[e[0] + bucket_offset] ^ ((uint8_t *)sf->array)[e[1] + bucket_offset] ^ ((uint8_t *)sf->array)[e[2] + bucket_offset];
#elif defined(SF_16)
	const int64_t result = ((uint16_t *)sf->array)[e[0] + bucket_offset] ^ ((uint16_t *)sf->array)[e[1] + bucket_offset] ^ ((uint16_t *)sf->array)[e[2] + bucket_offset];
#elif defined(SF_32)
	const int64_t result = ((uint32_t *)sf->array)[e[0] + bucket_offset] ^ ((uint32_t *)sf->array)[e[1] + bucket_offset] ^ ((uint32_t *)sf->array)[e[2] + bucket_offset];
#else
	const int64_t result = get_value(sf->array, e[0] + bucket_offset, sf->width) ^ get_value(sf->array, e[1] + bucket_offset, sf->width) ^ get_value(sf->array, e[2] + bucket_offset, sf->width);
#endif
#ifdef FILTER
	return ((result ^ signature[0]) & ((UINT64_C(1) << sf->width) - 1)) == 0;
#else
	return result;
#endif
}

int64_t sf3_get_signature(const sf *sf, const uint64_t signature[4]) {
	const int bucket = ((__uint128_t)(signature[0] >> 1) * (__uint128_t)sf->multiplier) >> 64;
	const uint64_t offset_seed = sf->offset_and_seed[bucket];
	const uint64_t bucket_offset = offset_seed & OFFSET_MASK;
	const int num_variables = (sf->offset_and_seed[bucket + 1] & OFFSET_MASK) - bucket_offset;
	unsigned int e[3];
	signature_to_equation(signature, offset_seed & ~OFFSET_MASK, num_variables, e);
#ifdef SF_8
	const int64_t result = ((uint8_t *)sf->array)[e[0] + bucket_offset] ^ ((uint8_t *)sf->array)[e[1] + bucket_offset] ^ ((uint8_t *)sf->array)[e[2] + bucket_offset];
#elif defined(SF_16)
	const int64_t result = ((uint16_t *)sf->array)[e[0] + bucket_offset] ^ ((uint16_t *)sf->array)[e[1] + bucket_offset] ^ ((uint16_t *)sf->array)[e[2] + bucket_offset];
#elif defined(SF_32)
	const int64_t result = ((uint32_t *)sf->array)[e[0] + bucket_offset] ^ ((uint32_t *)sf->array)[e[1] + bucket_offset] ^ ((uint32_t *)sf->array)[e[2] + bucket_offset];
#else
	const int64_t result = get_value(sf->array, e[0] + bucket_offset, sf->width) ^ get_value(sf->array, e[1] + bucket_offset, sf->width) ^ get_value(sf->array, e[2] + bucket_offset, sf->width);
#endif
#ifdef FILTER
	return ((result ^ signature[0]) & ((UINT64_C(1) << sf->width) - 1)) == 0;
#else
	return result;
#endif
}
