# Sourced by the scripts that run xcodebuild. Sets XCODE_CACHE_ARGS: Xcode compilation caching with one CAS
# shared by every worktree, so a fresh DerivedData reuses compiled Swift/C output from other trees.
# S2_XCODE_CAS overrides the path; S2_XCODE_CAS=off disables caching.
if [[ "${S2_XCODE_CAS:-}" == "off" ]]; then
  XCODE_CACHE_ARGS=()
else
  XCODE_CACHE_ARGS=(COMPILATION_CACHE_ENABLE_CACHING=YES
    "COMPILATION_CACHE_CAS_PATH=${S2_XCODE_CAS:-$HOME/Library/Caches/s2-xcode-cas}")
fi
