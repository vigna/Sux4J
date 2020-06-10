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

#include <inttypes.h>

#ifdef USE_MMAP
#include <sys/mman.h>
#include <sys/resource.h>
#define calloc(n, size) mmap((void *)(0x0UL), (n) * (size), PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_HUGETLB | (30 << MAP_HUGE_SHIFT), 0, 0)
#endif

typedef struct {
	uint64_t size;
	uint64_t multiplier;
	uint64_t global_seed;
	uint64_t edge_offset_and_seed_length;
	uint64_t *edge_offset_and_seed;
	uint64_t array_length;
	uint64_t *array;
} mph;

mph *load_mph(int h);
int64_t mph_get_byte_array(const mph *mph, char *key, uint64_t len);
int64_t mph_get_uint64_t(const mph *mph, uint64_t key);
int64_t mph_get_uint128_t(const mph *mph, __uint128_t key);
