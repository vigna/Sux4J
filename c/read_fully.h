/*
 * Sux: Succinct data structures
 *
 * Copyright (C) 2026 Sebastiano Vigna
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

#ifndef READ_FULLY_H
#define READ_FULLY_H

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

// A single read() might transfer fewer bytes than requested: Linux transfers
// at most 0x7ffff000 bytes per call, and macOS rejects requests larger than
// INT_MAX. Thus, we read in chunks of at most 1 GiB and exit on errors and
// on premature ends of file.
static void read_fully(int h, void *buf, size_t len) {
	char *p = (char *)buf;
	while (len != 0) {
		const ssize_t r = read(h, p, len < (1 << 30) ? len : (1 << 30));
		if (r <= 0) {
			if (r == 0) fprintf(stderr, "Unexpected end of file\n");
			else perror("read");
			exit(1);
		}
		p += r;
		len -= r;
	}
}

#endif
