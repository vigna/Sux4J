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

int main(int argc, char *argv[]) {
	int h = open(argv[1], O_RDONLY);
	assert(h >= 0);
	SUX4J_MAP *map = SUX4J_LOAD_MAP(h);
	close(h);

#define NKEYS 10000000
	uint64_t u = 0;
	uint64_t sample[SAMPLES];

	for (int k = SAMPLES; k-- != 0;) {
		uint64_t key = 0;

		int64_t elapsed = -get_system_time();
		for (int i = 0; i < NKEYS; ++i) {
			key += UINT64_C(0x9e3779b97f4a7c15);
			u += SUX4J_GET_UINT64_T(map, key);
		}
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
