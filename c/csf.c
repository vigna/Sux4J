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
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include "csf.h"
#include "read_fully.h"

csf *load_csf(int h) {
	csf *csf = calloc(1, sizeof *csf);
	read_fully(h, &csf->size, sizeof csf->size);
	uint64_t t;

	read_fully(h, &t, sizeof t);
	csf->multiplier = t;

	read_fully(h, &t, sizeof t);
	csf->global_max_codeword_length = t;

	read_fully(h, &csf->global_seed, sizeof csf->global_seed);
	read_fully(h, &csf->offset_and_seed_length, sizeof csf->offset_and_seed_length);
	csf->offset_and_seed = malloc(csf->offset_and_seed_length * sizeof *csf->offset_and_seed);
	read_fully(h, csf->offset_and_seed, csf->offset_and_seed_length * sizeof *csf->offset_and_seed);

	read_fully(h, &csf->array_length, sizeof csf->array_length);

	csf->array = malloc(csf->array_length * sizeof *csf->array);
	read_fully(h, csf->array, csf->array_length * sizeof *csf->array);

	// Decoder
	read_fully(h, &csf->escaped_symbol_length, sizeof csf->escaped_symbol_length);
	read_fully(h, &csf->escape_length, sizeof csf->escape_length);

	uint64_t decoding_table_length;
	read_fully(h, &decoding_table_length, sizeof decoding_table_length);

	uint64_t num_symbols;
	read_fully(h, &num_symbols, sizeof num_symbols);

	// Compact
	char *p = malloc(sizeof *csf + decoding_table_length * sizeof *csf->last_codeword_plus_one + decoding_table_length * sizeof *csf->how_many_up_to_block + (decoding_table_length + 7 & ~7ULL) * sizeof *csf->shift + num_symbols * sizeof *csf->symbol);
	csf = memcpy(p, csf, sizeof *csf);
	p += sizeof *csf;

	csf->last_codeword_plus_one = (uint64_t *)p;
	read_fully(h, csf->last_codeword_plus_one, decoding_table_length * sizeof *csf->last_codeword_plus_one);
	p += decoding_table_length * sizeof *csf->last_codeword_plus_one;

	csf->how_many_up_to_block = (uint32_t *)p;
	read_fully(h, csf->how_many_up_to_block, decoding_table_length * sizeof *csf->how_many_up_to_block);
	p += decoding_table_length * sizeof *csf->how_many_up_to_block;

	csf->shift = (uint8_t *)p;
	read_fully(h, csf->shift, decoding_table_length * sizeof *csf->shift);
	p += decoding_table_length + 7 & ~7ULL; // Realign

	csf->symbol = (uint64_t *)p;
	read_fully(h, csf->symbol, num_symbols * sizeof *csf->symbol);

	return csf;
}
