#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include "sf.h"

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
