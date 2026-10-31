#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
core="$repo_root/rust/core"
out_dir="${1:?usage: build-linux-native.sh OUTPUT_DIR}"

case "$(uname -m)" in
    x86_64) rust_target=x86_64-unknown-linux-gnu; resource_arch=linux-x64 ;;
    aarch64|arm64) rust_target=aarch64-unknown-linux-gnu; resource_arch=linux-arm64 ;;
    *) echo "Unsupported Linux architecture: $(uname -m)" >&2; exit 2 ;;
esac
if [[ "$(uname -s)" != Linux ]]; then
    echo "Linux native packaging must run on Linux." >&2
    exit 2
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
    java_bin="$(readlink -f "$(command -v java)")"
    export JAVA_HOME="$(dirname "$(dirname "$(dirname "$java_bin")")")"
fi

cargo build --manifest-path "$core/Cargo.toml" --release --target "$rust_target"
mkdir -p "$out_dir/natives/$resource_arch"
cc -shared -fPIC \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    -I"$core/include" \
    "$repo_root/mobile/shared/src/nativeInterop/jni/jni_bridge.c" \
    "$core/target/$rust_target/release/libcodeberg_core.a" \
    -ldl -lpthread -lm \
    -o "$out_dir/natives/$resource_arch/libcm_native.so"
