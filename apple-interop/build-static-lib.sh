#!/usr/bin/env bash
# Build the facade as one self-contained static archive per Apple target:
# `libtool` folds the Swift build output and its whole dependency closure
# into the archive the embedding klib carries.
#
# Usage: build-static-lib.sh <swift-triple> <apple-sdk> <output-dir>
set -ueo pipefail

triple=$1
sdk=$2
out=$3

package_dir=$(cd "$(dirname "$0")" && pwd)

swift build \
  --package-path "$package_dir" \
  --configuration release \
  --build-system native \
  --triple "$triple" \
  --sdk "$(xcrun --sdk "$sdk" --show-sdk-path)"

# The Swift objects autolink the toolchain's back-deployment archives,
# which the Kotlin/Native link step does not search; fold them in as well.
toolchain_swift_lib="$(dirname "$(dirname "$(xcrun --find swiftc)")")/lib/swift/$sdk"
compat_libs=()
for name in swiftCompatibility50 swiftCompatibility51 swiftCompatibility56 \
  swiftCompatibilityConcurrency swiftCompatibilityDynamicReplacements \
  swiftCompatibilityPacks; do
  lib="$toolchain_swift_lib/lib$name.a"
  if [[ -f "$lib" ]]; then compat_libs+=("$lib"); fi
done

# -arch_only: the universal toolchain archives must not fatten the merge.
mkdir -p "$out"
objects=$(mktemp)
trap 'rm -f "$objects"' EXIT
find "$package_dir/.build/$triple/release" -name '*.o' | sort > "$objects"
libtool -static -arch_only "${triple%%-*}" \
  -o "$out/libRoutexKotlinInterop.a" \
  -filelist "$objects" \
  ${compat_libs[@]+"${compat_libs[@]}"}
