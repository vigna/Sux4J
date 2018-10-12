#include <inttypes.h>

typedef struct {
	uint64_t size;
	int chunk_shift;
	int global_max_codeword_length;
	uint64_t global_seed;
	uint64_t offset_and_seed_length;
	uint64_t *offset_and_seed;
	uint64_t array_length;
	uint64_t *array;
	uint64_t *symbol;
	uint64_t *last_codeword_plus_one;
	uint32_t *how_many_up_to_block;
	uint32_t *shift;
} csf;

csf *load_csf(int h);
uint64_t decode(const csf * const csf, const uint64_t value);
