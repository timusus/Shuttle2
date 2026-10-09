# Sourced by the scripts that run xcodebuild. Sets XCODE_CACHE_ARGS: Xcode compilation caching with one CAS
# shared by every worktree, so a fresh DerivedData reuses compiled Swift/C output from other trees.
# S2_XCODE_CAS overrides the path; S2_XCODE_CAS=off disables caching. Expand the array as
# ${XCODE_CACHE_ARGS[@]+"${XCODE_CACHE_ARGS[@]}"}: macOS bash 3.2 treats an empty array as unset under set -u.
# Prefix mapping keeps each worktree's absolute paths out of shared entries; the limit (bytes) bounds the CAS.
if [[ "${S2_XCODE_CAS:-}" == "off" ]]; then
  XCODE_CACHE_ARGS=()
else
  XCODE_CACHE_ARGS=(COMPILATION_CACHE_ENABLE_CACHING=YES
    "COMPILATION_CACHE_CAS_PATH=${S2_XCODE_CAS:-$HOME/Library/Caches/s2-xcode-cas}"
    COMPILATION_CACHE_LIMIT_SIZE=10737418240
    SWIFT_ENABLE_PREFIX_MAPPING=YES SWIFT_ENABLE_PROJECT_PREFIX_MAPPING=YES
    CLANG_ENABLE_PREFIX_MAPPING=YES CLANG_ENABLE_PROJECT_PREFIX_MAPPING=YES)
fi
