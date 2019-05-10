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
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include "sf.h"

sf *load_sf(int h) {
	sf *sf = calloc(1, sizeof *sf);
	read(h, &sf->size, sizeof sf->size);
	uint64_t t;
	read(h, &t, sizeof t);
	sf->width = t;
	read(h, &t, sizeof t);
	sf->multiplier = t;
	read(h, &sf->global_seed, sizeof sf->global_seed);
	read(h, &sf->offset_and_seed_length, sizeof sf->offset_and_seed_length);
	sf->offset_and_seed = calloc(sf->offset_and_seed_length, sizeof *sf->offset_and_seed);
	read(h, sf->offset_and_seed, sf->offset_and_seed_length * sizeof *sf->offset_and_seed);

	read(h, &sf->array_length, sizeof sf->array_length);
	sf->array = calloc(sf->array_length, sizeof *sf->array);
	read(h, sf->array, sf->array_length * sizeof *sf->array);
	return sf;
}
