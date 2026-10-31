// SPDX-License-Identifier: AGPL-3.0-or-later
#ifndef CODEBERG_CORE_H
#define CODEBERG_CORE_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
size_t codeberg_line_count(const char *data, size_t len);
#ifdef __cplusplus
}
#endif
#endif
