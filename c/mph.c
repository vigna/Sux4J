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
#include "spooky.h"
#include "mph.h"

mph *load_mph(int h) {
	mph *mph = calloc(1, sizeof *mph);
	read(h, &mph->size, sizeof mph->size);
	uint64_t t;
	read(h, &t, sizeof t);
	mph->multiplier = t;
	read(h, &mph->global_seed, sizeof mph->global_seed);
	read(h, &mph->edge_offset_and_seed_length, sizeof mph->edge_offset_and_seed_length);
	mph->edge_offset_and_seed = calloc(mph->edge_offset_and_seed_length, sizeof *mph->edge_offset_and_seed);
	read(h, mph->edge_offset_and_seed, mph->edge_offset_and_seed_length * sizeof *mph->edge_offset_and_seed);

	read(h, &mph->array_length, sizeof mph->array_length);
	mph->array = calloc(mph->array_length, sizeof *mph->array);
	read(h, mph->array, mph->array_length * sizeof *mph->array);
	return mph;
}

static int inline _count_nonzero_pairs(const uint64_t x) {
	return __builtin_popcountll((x | x >> 1) & 0x5555555555555555);
}

static uint64_t inline count_nonzero_pairs(const uint64_t start, const uint64_t end, const uint64_t * const array) {
	int block = start / 32;
	const int end_block = end / 32;
	const int start_offset = start % 32;
	const int end_offset = end % 32;

	if (block == end_block) return _count_nonzero_pairs((array[block] & (UINT64_C(1) << end_offset * 2) - 1) >> start_offset * 2);
	uint64_t pairs = 0;
	if (start_offset != 0) pairs += _count_nonzero_pairs(array[block++] >> start_offset * 2);
	while(block < end_block) pairs += _count_nonzero_pairs(array[block++]);
	if (end_offset != 0) pairs += _count_nonzero_pairs(array[block] & (UINT64_C(1) << end_offset * 2) - 1);
	return pairs;
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
																										

#define OFFSET_MASK (UINT64_C(-1) >> 8)
#define C_TIMES_256 (int)(floor((1.09 + 0.01) * 256))

static uint64_t inline vertex_offset(const uint64_t edge_offset_seed) {
	return ((edge_offset_seed & OFFSET_MASK) * C_TIMES_256 >> 8);
}

static int inline get_2bit_value(uint64_t *array, uint64_t pos) {
	pos *= 2;
	return array[pos / 64] >> pos % 64 & 3;
}

int64_t mph_get_byte_array(const mph *mph, char *key, uint64_t len) {
	uint64_t signature[4];
	spooky_short(key, len, mph->global_seed, signature);
	const int bucket = ((__uint128_t)(signature[0] >> 1) * (__uint128_t)mph->multiplier) >> 64;
	const uint64_t edge_offset_seed = mph->edge_offset_and_seed[bucket];
	const uint64_t bucket_offset = vertex_offset(edge_offset_seed);
	const int num_variables = vertex_offset(mph->edge_offset_and_seed[bucket + 1]) - bucket_offset;
	int e[3];
	signature_to_equation(signature, edge_offset_seed & ~OFFSET_MASK, num_variables, e);
	return (edge_offset_seed & OFFSET_MASK) + count_nonzero_pairs(bucket_offset, bucket_offset + e[(get_2bit_value(mph->array, e[0] + bucket_offset) + get_2bit_value(mph->array, e[1] + bucket_offset) + get_2bit_value(mph->array, e[2] + bucket_offset)) % 3], mph->array);
}

int64_t mph_get_uint64_t(const mph *mph, const uint64_t key) {
	uint64_t signature[4];
	spooky_short(&key, 8, mph->global_seed, signature);
	const int bucket = ((__uint128_t)(signature[0] >> 1) * (__uint128_t)mph->multiplier) >> 64;
	const uint64_t edge_offset_seed = mph->edge_offset_and_seed[bucket];
	const uint64_t bucket_offset = vertex_offset(edge_offset_seed);
	const int num_variables = vertex_offset(mph->edge_offset_and_seed[bucket + 1]) - bucket_offset;
	int e[3];
	signature_to_equation(signature, edge_offset_seed & ~OFFSET_MASK, num_variables, e);
	return (edge_offset_seed & OFFSET_MASK) + count_nonzero_pairs(bucket_offset, bucket_offset + e[(get_2bit_value(mph->array, e[0] + bucket_offset) + get_2bit_value(mph->array, e[1] + bucket_offset) + get_2bit_value(mph->array, e[2] + bucket_offset)) % 3], mph->array);
}
