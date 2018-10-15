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
#include "sf4.h"
#include "spooky.h"

static void inline triple_to_equation(const uint64_t *triple, const uint64_t seed, int num_variables, int *e) {
	uint64_t hash[4];
	spooky_short_rehash(triple, seed, hash);
	const int shift = __builtin_clzll(num_variables);
	const uint64_t mask = (UINT64_C(1) << shift) - 1;
	e[0] = ((hash[0] & mask) * num_variables) >> shift;
	e[1] = ((hash[1] & mask) * num_variables) >> shift;
	e[2] = ((hash[2] & mask) * num_variables) >> shift;
	e[3] = ((hash[3] & mask) * num_variables) >> shift;
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

int64_t sf4_get_byte_array(const sf *sf, char *key, uint64_t len) {
	uint64_t h[4];
	spooky_short(key, len, sf->global_seed, h);
	const int chunk = h[0] >> sf->chunk_shift;
	const uint64_t offset_seed = sf->offset_and_seed[chunk];
	const uint64_t chunk_offset = offset_seed & OFFSET_MASK;
	const int num_variables = (sf->offset_and_seed[chunk + 1] & OFFSET_MASK) - chunk_offset;
	int e[4];
	triple_to_equation(h, offset_seed & ~OFFSET_MASK, num_variables, e);
	return get_value(sf->array, e[0] + chunk_offset, sf->width) ^ get_value(sf->array, e[1] + chunk_offset, sf->width) ^ get_value(sf->array, e[2] + chunk_offset, sf->width) ^ get_value(sf->array, e[3] + chunk_offset, sf->width);
}

int64_t sf4_get_uint64_t(const sf *sf, const uint64_t key) {
	uint64_t h[4];
	spooky_short(&key, 8, sf->global_seed, h);
	const int chunk = h[0] >> sf->chunk_shift;
	const uint64_t offset_seed = sf->offset_and_seed[chunk];
	const uint64_t chunk_offset = offset_seed & OFFSET_MASK;
	const int num_variables = (sf->offset_and_seed[chunk + 1] & OFFSET_MASK) - chunk_offset;
	int e[4];
	triple_to_equation(h, offset_seed & ~OFFSET_MASK, num_variables, e);
	return get_value(sf->array, e[0] + chunk_offset, sf->width) ^ get_value(sf->array, e[1] + chunk_offset, sf->width) ^ get_value(sf->array, e[2] + chunk_offset, sf->width) ^ get_value(sf->array, e[3] + chunk_offset, sf->width);
}
