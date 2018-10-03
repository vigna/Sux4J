#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <math.h>
#include "sf.h"
#include "spooky.h"

sf *load_sf(int h) {
	sf *sf = calloc(1, sizeof *sf);
	read(h, &sf->size, sizeof sf->size);
	uint64_t t;
	read(h, &t, sizeof t);
	sf->width = t;
	read(h, &t, sizeof t);
	sf->chunk_shift = t;
	read(h, &sf->global_seed, sizeof sf->global_seed);
	read(h, &sf->offset_and_seed_length, sizeof sf->offset_and_seed_length);
	sf->offset_and_seed = calloc(sf->offset_and_seed_length, sizeof *sf->offset_and_seed);
	read(h, sf->offset_and_seed, sf->offset_and_seed_length * sizeof *sf->offset_and_seed);

	read(h, &sf->array_length, sizeof sf->array_length);
	sf->array = calloc(sf->array_length, sizeof *sf->array);
	read(h, sf->array, sf->array_length * sizeof *sf->array);
	return sf;
}

static void inline triple_to_equation(const uint64_t *triple, const uint64_t seed, int num_variables, int *e) {
	uint64_t hash[4];
	spooky_short_rehash(triple, seed, hash);
	const int shift = __builtin_clzll(num_variables);
	const uint64_t mask = (UINT64_C(1) << shift) - 1;
	e[0] = ((hash[0] & mask) * num_variables) >> shift;
	e[1] = ((hash[1] & mask) * num_variables) >> shift;
	e[2] = ((hash[2] & mask) * num_variables) >> shift;
}
																										

#define OFFSET_MASK (UINT64_C(-1) >> 8)
#define C_TIMES_256 (int)(floor((1.09 + 0.01) * 256))

static uint64_t inline vertex_offset(const uint64_t offset_seed) {
	return ((offset_seed & OFFSET_MASK) * C_TIMES_256 >> 8);
}

static uint64_t get_value(const uint64_t * const array, uint64_t pos, const int width) {
	pos *= width;
	const int l = 64 - width;
	const int start_word = pos / 64;
	const int start_bit = pos % 64;
	if (start_bit <= l) return array[start_word] << l - start_bit >> l;
	return array[start_word] >> start_bit | array[start_word + 1] << 64 + l - start_bit >> l;
}

int64_t get_byte_array(const sf *sf, char *key, uint64_t len) {
	uint64_t h[4];
	spooky_short(key, len, sf->global_seed, h);
	const int chunk = h[0] >> sf->chunk_shift;
	const uint64_t offset_seed = sf->offset_and_seed[chunk];
	const uint64_t chunk_offset = offset_seed & OFFSET_MASK;
	const int num_variables = (sf->offset_and_seed[chunk + 1] & OFFSET_MASK) - chunk_offset;
	if (num_variables == 0) return -1;
	int e[3];
	triple_to_equation(h, offset_seed & ~OFFSET_MASK, num_variables, e);
	return get_value(sf->array, e[0] + chunk_offset, sf->width) ^ get_value(sf->array, e[1] + chunk_offset, sf->width) ^ get_value(sf->array, e[2] + chunk_offset, sf->width);
}

int64_t get_uint64_t(const sf *sf, const uint64_t key) {
	uint64_t h[4];
	spooky_short(&key, 8, sf->global_seed, h);
	const int chunk = h[0] >> sf->chunk_shift;
	const uint64_t offset_seed = sf->offset_and_seed[chunk];
	const uint64_t chunk_offset = offset_seed & OFFSET_MASK;
	const int num_variables = (sf->offset_and_seed[chunk + 1] & OFFSET_MASK) - chunk_offset;
	if (num_variables == 0) return -1;
	int e[3];
	triple_to_equation(h, offset_seed & ~OFFSET_MASK, num_variables, e);
	return get_value(sf->array, e[0] + chunk_offset, sf->width) ^ get_value(sf->array, e[1] + chunk_offset, sf->width) ^ get_value(sf->array, e[2] + chunk_offset, sf->width);
}
