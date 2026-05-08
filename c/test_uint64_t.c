/*
 * Sux: Succinct data structures
 *
 * Copyright (C) 2018-2025 Sebastiano Vigna
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

#include <stdio.h>
#include <inttypes.h>
#include <fcntl.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>
#include <sys/time.h>
#include <sys/resource.h>

#define SAMPLES 11

static uint64_t get_system_time(void) {
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return tv.tv_sec * 1000000 + tv.tv_usec;
}

static int cmp_uint64_t(const void *a, const void *b) {
	return *(uint64_t *)a < *(uint64_t *)b ? -1 : *(uint64_t *)a > *(uint64_t *)b ? 1 : 0;
}

static inline uint64_t rotl(const uint64_t x, int k) {
	return (x << k) | (x >> (64 - k));
}

static uint64_t s[2];

static uint64_t next(void) {
	const uint64_t s0 = s[0];
	uint64_t s1 = s[1];
	const uint64_t result = s0 + s1;
	s1 ^= s0;
	s[0] = rotl(s0, 24) ^ s1 ^ (s1 << 16);
	s[1] = rotl(s1, 37);
	return result;
}

int main(int argc, char *argv[]) {
	int h = open(argv[1], O_RDONLY);
	assert(h >= 0);
	SUX4J_MAP *map = SUX4J_LOAD_MAP(h);
	close(h);

#define NKEYS 10000000
	uint64_t u = 0;
	uint64_t sample[SAMPLES];

	for (int k = SAMPLES; k-- != 0;) {
		s[0] = 0x5603141978c51071;
		s[1] = 0x3bbddc01ebdf4b72;

		int64_t elapsed = -get_system_time();
		for (int i = 0; i < NKEYS; ++i)
			u += SUX4J_GET_UINT64_T(map, next());
		elapsed += get_system_time();

		sample[k] = elapsed;
	}
	const volatile uint64_t unused = u;

	qsort(sample, SAMPLES, sizeof *sample, cmp_uint64_t);

	double min_ns = sample[0] * 1000.0 / NKEYS;
	double median_ns = sample[SAMPLES / 2] * 1000.0 / NKEYS;
	double max_ns = sample[SAMPLES - 1] * 1000.0 / NKEYS;
	double sum = 0;
	for (int k = 0; k < SAMPLES; k++) sum += sample[k];
	double avg_ns = sum * 1000.0 / NKEYS / SAMPLES;

	printf("Min: %.1f Median: %.1f Max: %.1f Average: %.1f\n",
	       min_ns, median_ns, max_ns, avg_ns);
}
