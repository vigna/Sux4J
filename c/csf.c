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
#include "csf.h"

csf *load_csf(int h) {
	csf *csf = calloc(1, sizeof *csf);
	read(h, &csf->size, sizeof csf->size);
	uint64_t t;

	read(h, &t, sizeof t);
	csf->chunk_shift = t;

	read(h, &t, sizeof t);
	csf->global_max_codeword_length = t;

	read(h, &csf->global_seed, sizeof csf->global_seed);
	read(h, &csf->offset_and_seed_length, sizeof csf->offset_and_seed_length);
	csf->offset_and_seed = calloc(csf->offset_and_seed_length, sizeof *csf->offset_and_seed);
	read(h, csf->offset_and_seed, csf->offset_and_seed_length * sizeof *csf->offset_and_seed);

	read(h, &csf->array_length, sizeof csf->array_length);

	csf->array = calloc(csf->array_length, sizeof *csf->array);
	read(h, csf->array, csf->array_length * sizeof *csf->array);

	uint64_t decoding_table_length;
	read(h, &decoding_table_length, sizeof decoding_table_length);

	csf->last_codeword_plus_one = calloc(decoding_table_length, sizeof *csf->last_codeword_plus_one);
	csf->how_many_up_to_block = calloc(decoding_table_length, sizeof *csf->how_many_up_to_block);
	csf->shift = calloc(decoding_table_length, sizeof *csf->shift);

	read(h, csf->last_codeword_plus_one, decoding_table_length * sizeof *csf->last_codeword_plus_one);
	read(h, csf->how_many_up_to_block, decoding_table_length * sizeof *csf->how_many_up_to_block);
	read(h, csf->shift, decoding_table_length * sizeof *csf->shift);

	uint64_t num_symbols;
	read(h, &num_symbols, sizeof num_symbols);

	csf->symbol = calloc(decoding_table_length, num_symbols * sizeof *csf->symbol);
	read(h, csf->symbol, num_symbols * sizeof *csf->symbol);

	return csf;
}
