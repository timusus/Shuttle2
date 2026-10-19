# shellcheck shell=bash
# Module and changed-file helpers for support/scripts/unit-test. Sourced, not run.

# Find all Gradle module paths declared via `include '...'` / `include(...)`
# in settings.gradle or settings.gradle.kts.
find_settings_file() {
  if [ -f "${REPO_ROOT}/settings.gradle.kts" ]; then
    echo "${REPO_ROOT}/settings.gradle.kts"
  elif [ -f "${REPO_ROOT}/settings.gradle" ]; then
    echo "${REPO_ROOT}/settings.gradle"
  else
    echo "error: no settings.gradle(.kts) found in ${REPO_ROOT}" >&2
    exit 1
  fi
}

all_module_paths() {
  local settings_file
  settings_file="$(find_settings_file)"
  grep -Eo "include[[:space:]]*\(?[\"']:[^\"']+[\"']" "${settings_file}" \
    | sed -E "s/^include[[:space:]]*\(?[\"']//; s/[\"']$//"
}

# Resolve a module argument (short name or Gradle path) to a Gradle path.
# Prints the resolved path to stdout, or prints candidates to stderr and
# returns 1 if ambiguous/unknown.
resolve_module() {
  local input="$1"
  local all_paths
  all_paths="$(all_module_paths)"

  # Already a full Gradle path.
  if [ "${input#:}" != "${input}" ]; then
    if printf '%s\n' "${all_paths}" | grep -Fxq -- "${input}"; then
      echo "${input}"
      return 0
    else
      echo "error: unknown module path '${input}'" >&2
      return 1
    fi
  fi

  # Short name: prefer an exact ":android:<name>" (top-level module) match,
  # since that's unambiguous by construction; otherwise fall back to
  # matching on the last path segment across all modules.
  local direct_match=":android:${input}"
  if printf '%s\n' "${all_paths}" | grep -Fxq -- "${direct_match}"; then
    echo "${direct_match}"
    return 0
  fi

  local matches
  matches="$(printf '%s\n' "${all_paths}" | awk -F: -v n="${input}" '$NF == n')"

  local count
  count="$(printf '%s\n' "${matches}" | grep -c . || true)"

  if [ "${count}" -eq 1 ]; then
    echo "${matches}"
    return 0
  elif [ "${count}" -gt 1 ]; then
    echo "error: module name '${input}' is ambiguous, candidates:" >&2
    printf '%s\n' "${matches}" | sed 's/^/  /' >&2
    return 1
  else
    echo "error: unknown module '${input}', available modules:" >&2
    printf '%s\n' "${all_paths}" | sed 's/^/  /' >&2
    return 1
  fi
}

# A module's directory from its Gradle path: ":android:mediaprovider:core" -> android/mediaprovider/core,
# ":shared" -> shared.
module_dir_for_path() {
  printf '%s' "${1#:}" | tr ':' '/'
}

# Prints "<module-dir>\t<gradle-path>" for every declared module, e.g.
# "android/mediaprovider/core\t:android:mediaprovider:core".
module_dir_path_pairs() {
  local p dir
  while IFS= read -r p; do
    dir="$(module_dir_for_path "${p}")"
    printf '%s\t%s\n' "${dir}" "${p}"
  done < <(all_module_paths)
}

# Resolves a repo-relative file path to the Gradle path of the module whose
# directory it falls under (longest-prefix match), printing
# "<module-dir>\t<gradle-path>", or nothing if it isn't inside any module.
module_dir_and_path_for_file() {
  local file="$1"
  local dir path best_dir="" best_path=""
  while IFS=$'\t' read -r dir path; do
    case "${file}" in
      "${dir}"/*)
        if [ "${#dir}" -gt "${#best_dir}" ]; then
          best_dir="${dir}"
          best_path="${path}"
        fi
        ;;
    esac
  done < <(module_dir_path_pairs)
  [ -n "${best_path}" ] && printf '%s\t%s\n' "${best_dir}" "${best_path}"
}

# True (exit 0) if :android:app's unit tests exercise the module's main code at runtime. A deny list,
# so a new module triggers the app suite until someone checks that no app test imports it.
app_tests_cover_module() {
  case "$1" in
    :android:app | :shared | :android:architecture-tests | :android:baselineprofile | :android:networking | :android:saf \
      | :android:mediaprovider:emby | :android:mediaprovider:plex | :android:mediaprovider:subsonic \
      | :android:mediaprovider:server-testing) return 1 ;;
    *) return 0 ;;
  esac
}

# A filesystem-safe id for a Gradle path, used as a filename stem under
# WORK_DIR to track per-module state without bash 4 associative arrays.
module_id() {
  printf '%s' "${1#:}" | tr ':' '_'
}

# The module's build file (build.gradle.kts or build.gradle), or nothing if it has none.
module_build_file() {
  local mpath="$1" dir build_file
  dir="$(module_dir_for_path "${mpath}")"
  for build_file in "${REPO_ROOT}/${dir}/build.gradle.kts" "${REPO_ROOT}/${dir}/build.gradle"; do
    [ -f "${build_file}" ] && { echo "${build_file}"; return; }
  done
}

# True (exit 0) if a module applies the s2.kmp-library convention (:android:domain, #582).
module_is_kmp() {
  local build_file
  build_file="$(module_build_file "$1")"
  [ -n "${build_file}" ] && grep -q 's2\.kmp-library' "${build_file}"
}

# True (exit 0) if a module's build file doesn't apply the Android Gradle plugin: a plain
# Kotlin/JVM module (:android:architecture-tests) compiles its unit-test sources under the
# plain Kotlin task name, not the Android variant-specific one.
module_is_jvm_only() {
  local build_file
  build_file="$(module_build_file "$1")"
  [ -n "${build_file}" ] && ! grep -Eq 'com\.android\.(application|library)' "${build_file}"
}

# True (exit 0) if a module applies the Roborazzi plugin (its build file applies
# it by catalog alias or plugin id; a bare "roborazzi" grep would also match the
# dependency and config-block lines): it has screenshot tests and a
# verifyRoborazziDebug task (#831).
module_has_roborazzi() {
  local build_file
  build_file="$(module_build_file "$1")"
  [ -n "${build_file}" ] && grep -Eq 'libs\.plugins\.roborazzi|io\.github\.takahirom\.roborazzi' "${build_file}"
}

# Every declared module path that applies the Roborazzi plugin, one per line.
roborazzi_module_paths() {
  local p
  while IFS= read -r p; do
    module_has_roborazzi "${p}" && printf '%s\n' "${p}"
  done < <(all_module_paths)
}

# The task that compiles a module's unit-test sources: the Android host-test task for a KMP
# module, the plain Kotlin/JVM name for a module without the Android plugin, the Android variant
# task otherwise. The s2.kmp-library convention and :android:architecture-tests each register a testDebugUnitTest task as a twin of `test` so the
# *test-run* task name needs no such handling (#556), but there is no equivalent twin for the
# compile task.
compile_test_task_for() {
  if module_is_kmp "$1"; then
    echo "compileAndroidHostTest"
  elif module_is_jvm_only "$1"; then
    echo "compileTestKotlin"
  else
    echo "compileDebugUnitTestKotlin"
  fi
}

# Echoes the changed files: committed since the merge-base with ${base},
# plus uncommitted (staged/unstaged) and untracked changes. One path per
# line, deduplicated.
changed_files_since() {
  local base="$1" merge_base
  # UNIT_TEST_CHANGED_FILES=<file>: use the paths listed there (one per line) instead of the diff, so
  # land-verify can run the same task set on origin/main (#829).
  if [ -n "${UNIT_TEST_CHANGED_FILES:-}" ]; then
    sort -u "${UNIT_TEST_CHANGED_FILES}"
    return 0
  fi
  merge_base="$(git -C "${REPO_ROOT}" merge-base "${base}" HEAD 2>/dev/null)" || return 1
  {
    git -C "${REPO_ROOT}" diff --name-only "${merge_base}" HEAD
    git -C "${REPO_ROOT}" diff --name-only HEAD
    git -C "${REPO_ROOT}" ls-files --others --exclude-standard
  } | sort -u
}

# First changed file that is a build-system-wide change (buildSrc, the Gradle wrapper/version
# catalog, root build/settings files): it can affect any module's classpath or task graph, so
# per-module mapping doesn't apply. Prints nothing when none changed.
build_system_trigger() {
  printf '%s\n' "$1" | grep -E \
    '^buildSrc/|^gradle/|^build\.gradle(\.kts)?$|^settings\.gradle(\.kts)?$|^gradle\.properties$' \
    | head -1 || true
}

print_and_run() {
  echo "+ ${GRADLE_RUNNER[*]} $*"
  if [ "${DRY_RUN}" -eq 1 ]; then
    return 0
  fi
  run_and_report "$@"
}

# --kmp-native-tasks: the :<module>:iosSimulatorArm64Test task of every KMP module (s2.kmp-library)
# whose commonMain/commonTest/iosMain/iosTest sources the given files fall under, sorted and unique.
# Other source sets (androidMain, androidHostTest), build files and non-KMP modules map to nothing:
# Kotlin/Native compiles only those four, and full-verify.sh runs every module's tests anyway.
kmp_native_tasks_for_files() {
  local f mdp mdir mpath rel
  for f in "$@"; do
    mdp="$(module_dir_and_path_for_file "${f}")" || continue
    [ -n "${mdp}" ] || continue
    mdir="${mdp%%$'\t'*}"
    mpath="${mdp#*$'\t'}"
    rel="${f#"${mdir}"/}"
    case "${rel}" in
      src/commonMain/*|src/commonTest/*|src/iosMain/*|src/iosTest/*) ;;
      *) continue ;;
    esac
    if module_is_kmp "${mpath}"; then printf '%s:iosSimulatorArm64Test\n' "${mpath}"; fi
  done | sort -u
}

# After a --changed / --changed-tests run: when the diff touches commonTest or iosTest sources, also run
# NativeTestNameRules (Kotlin/Native-illegal backtick names, #908), which otherwise only fails at landing.
# Returns the task's exit code, or 0 when nothing applies.
run_native_name_rules_if_needed() {
  local changed_files
  changed_files="$(changed_files_since "$1")" || return 0
  printf '%s\n' "${changed_files}" | grep -qE '/src/(commonTest|iosTest)/' || return 0
  echo
  echo "commonTest/iosTest changed: running NativeTestNameRules"
  print_and_run :android:architecture-tests:testDebugUnitTest --tests '*NativeTestNameRules*'
}
