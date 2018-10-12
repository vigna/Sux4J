#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <math.h>
#include "csf3.h"
#include "spooky.h"

static void inline triple_to_equation(const uint64_t *triple, const uint64_t seed, int num_variables, int *e) {
	uint64_t hash[4];
	spooky_short_rehash(triple, seed, hash);
	const int shift = __builtin_clzll(num_variables);
	const uint64_t mask = (UINT64_C(1) << shift) - 1;
	e[0] = ((hash[0] & mask) * num_variables) >> shift;
	e[1] = ((hash[1] & mask) * num_variables) >> shift;
	e[2] = ((hash[2] & mask) * num_variables) >> shift;
}
																										

#define OFFSET_MASK (UINT64_C(-1) >> 10)

static uint64_t get_value(const uint64_t * const array, uint64_t pos, const int width) {
	const int l = 64 - width;
	const int start_word = pos / 64;
	const int start_bit = pos % 64;
	if (start_bit <= l) return array[start_word] << l - start_bit >> l;
	return array[start_word] >> start_bit | array[start_word + 1] << 64 + l - start_bit >> l;
}

int64_t csf3_get_byte_array(const csf *csf, char *key, uint64_t len) {
	uint64_t h[4];
	spooky_short(key, len, csf->global_seed, h);
	const int chunk = h[0] >> csf->chunk_shift;
	const uint64_t offset_seed = csf->offset_and_seed[chunk];
	const uint64_t chunk_offset = offset_seed & OFFSET_MASK;
	const int w = csf->global_max_codeword_length;
	const int num_variables = (csf->offset_and_seed[chunk + 1] & OFFSET_MASK) - chunk_offset - w;
	int e[3];
	triple_to_equation(h, offset_seed & ~OFFSET_MASK, num_variables, e);
	return decode(csf, get_value(csf->array, e[0] + chunk_offset, w) ^ get_value(csf->array, e[1] + chunk_offset, w) ^ get_value(csf->array, e[2] + chunk_offset, w));
}

int64_t csf3_get_uint64_t(const csf *csf, const uint64_t key) {
	uint64_t h[4];
	spooky_short(&key, 8, csf->global_seed, h);
	const int chunk = h[0] >> csf->chunk_shift;
	const uint64_t offset_seed = csf->offset_and_seed[chunk];
	const uint64_t chunk_offset = offset_seed & OFFSET_MASK;
	const int w = csf->global_max_codeword_length;
	const int num_variables = (csf->offset_and_seed[chunk + 1] & OFFSET_MASK) - chunk_offset - w;
	int e[3];
	triple_to_equation(h, offset_seed & ~OFFSET_MASK, num_variables, e);
	return decode(csf, get_value(csf->array, e[0] + chunk_offset, w) ^ get_value(csf->array, e[1] + chunk_offset, w) ^ get_value(csf->array, e[2] + chunk_offset, w));
}
