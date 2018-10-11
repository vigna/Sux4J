#include <inttypes.h>

typedef struct {
	uint64_t size;
	int width;
	int chunk_shift;
	uint64_t global_seed;
	uint64_t offset_and_seed_length;
	uint64_t *offset_and_seed;
	uint64_t array_length;
	uint64_t *array;
} sf;

sf *load_sf(int h);
