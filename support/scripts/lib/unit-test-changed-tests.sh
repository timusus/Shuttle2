# shellcheck shell=bash
# unit-test --changed-tests. Sourced, not run; needs unit-test-modules.sh and unit-test-changed.sh.

# Maps one changed src/main/*.kt file to the test class names in its module
# that exercise it, one per line (nothing if none map): itself, if a
# "<class>Test.kt" file exists anywhere under the given test source dirs, plus any
# "*Test.kt" that references the class by name (plain substring grep over the
# test sources, kept simple and predictable per #552). A ViewModel also
# matches on its name with the "ViewModel" suffix stripped, to catch a
# screen's Robot-based test that exercises it without naming the ViewModel
# class directly (e.g. LibraryScreenTest for SongListViewModel, since SongList
# is part of the Library screen rather than its own).
classes_for_main_file() {
  local abs_file="$1" cls stem hit d test_dirs=()
  shift
  for d in "$@"; do
    [ -d "${d}" ] && test_dirs+=("${d}")
  done
  cls="$(basename "${abs_file}" .kt)"
  [ "${#test_dirs[@]}" -gt 0 ] || return 0

  if find "${test_dirs[@]}" -type f -name "${cls}Test.kt" 2>/dev/null | grep -q .; then
    echo "${cls}Test"
  fi

  while IFS= read -r hit; do
    [ -z "${hit}" ] && continue
    basename "${hit}" .kt
  done < <(grep -rl --include='*Test.kt' -F -- "${cls}" "${test_dirs[@]}" 2>/dev/null || true)

  stem="${cls%ViewModel}"
  if [ "${stem}" != "${cls}" ] && [ -n "${stem}" ]; then
    while IFS= read -r hit; do
      [ -z "${hit}" ] && continue
      basename "${hit}" .kt
    done < <(grep -rl --include='*Test.kt' -F -- "${stem}" "${test_dirs[@]}" 2>/dev/null || true)
  fi
}

# Records, under WORK_DIR, the test classes (or whole-module fallbacks) each changed file maps to.
classify_changed_tests_files() {
  local changed_files="$1" mdir mpath id rel f cls
  while IFS= read -r f; do
    [ -z "${f}" ] && continue
    case "${f}" in
      android/* | shared/*) ;;
      *) continue ;;
    esac

    IFS=$'\t' read -r mdir mpath <<< "$(module_dir_and_path_for_file "${f}")"
    if [ -z "${mpath}" ]; then
      echo "note: '${f}' is under android/ or shared/ but isn't part of a registered module; skipping"
      continue
    fi
    if [ "${mpath}" = ":android:baselineprofile" ]; then
      echo "note: '${f}' is in ${mpath}, an instrumented test module with no unit tests; skipping"
      continue
    fi
    id="$(module_id "${mpath}")"
    echo "${mpath}" >> "${WORK_DIR}/modules_seen"
    rel="${f#"${mdir}"/}"

    case "${rel}" in
      src/test/*Test.kt | src/commonTest/*Test.kt | src/androidHostTest/*Test.kt)
        # A screenshot test belongs to the final verify (Roborazzi runs in
        # verify mode there), not a --tests run here; treat it like any
        # other change that maps to no runnable class.
        # A benchmark test is excluded from the default sweep (build.gradle.kts,
        # -Ps2.runBenchmarks=true opts in), so a --tests filter on it dies with
        # "No tests found" (#680); skip it instead of a whole-module run, which
        # excludes benchmarks too.
        cls="$(basename "${rel}" .kt)"
        if [ -f "${REPO_ROOT}/${f}" ] && [ "${cls%BenchmarkTest}" != "${cls}" ]; then
          echo "note: '${f}' is a benchmark test, outside the default sweep (opt in with -Ps2.runBenchmarks=true); skipping"
          touch "${WORK_DIR}/${id}.benchmark"
        elif [ -f "${REPO_ROOT}/${f}" ] && [ "${cls%ScreenshotTest}" = "${cls}" ]; then
          echo "${cls}" >> "${WORK_DIR}/${id}.classes"
        elif [ -f "${REPO_ROOT}/${f}" ]; then
          echo "note: '${f}' is a screenshot test; falling back to a whole run of ${mpath}"
          touch "${WORK_DIR}/${id}.unmappable"
        fi
        ;;
      src/androidTest/*.kt)
        echo "note: '${f}' is an androidTest source; falling back to a whole run of ${mpath}"
        touch "${WORK_DIR}/${id}.unmappable"
        ;;
      src/main/*.kt | src/test/*.kt | src/commonMain/*.kt | src/androidMain/*.kt | src/iosMain/*.kt | src/commonTest/*.kt | src/androidHostTest/*.kt)
        # A main file, or a test helper (a Robot, Scenarios, a fake), selects
        # the test classes that use it; a helper has no @Test methods itself.
        local found=0 name
        while IFS= read -r name; do
          [ -z "${name}" ] && continue
          [ "${name%ScreenshotTest}" != "${name}" ] && continue
          [ "${name%BenchmarkTest}" != "${name}" ] && { touch "${WORK_DIR}/${id}.benchmark"; continue; }
          echo "${name}" >> "${WORK_DIR}/${id}.classes"
          found=1
        done < <(classes_for_main_file "${REPO_ROOT}/${f}" "${REPO_ROOT}/${mdir}/src/test" "${REPO_ROOT}/${mdir}/src/commonTest" "${REPO_ROOT}/${mdir}/src/androidHostTest")
        if [ "${found}" -eq 0 ]; then
          echo "note: '${f}' maps to no test class; falling back to a whole run of ${mpath}"
          touch "${WORK_DIR}/${id}.unmappable"
        fi
        ;;
      *)
        echo "note: '${f}' is a non-Kotlin change; falling back to a whole run of ${mpath}"
        touch "${WORK_DIR}/${id}.unmappable"
        ;;
    esac
  done <<< "${changed_files}"
  return 0
}

# Runs the classified modules in one Gradle invocation, printing each module's plan.
run_changed_tests_batch() {
  local overall_rc=0 whole_batch=() run_tasks=() m mid unmappable_file classes_file classes_sorted count preview cls_line
  while IFS= read -r m; do
    mid="$(module_id "${m}")"
    unmappable_file="${WORK_DIR}/${mid}.unmappable"
    classes_file="${WORK_DIR}/${mid}.classes"

    if [ -f "${unmappable_file}" ]; then
      echo "${m}: running whole (no class-level mapping)"
      whole_batch+=("${m}:testDebugUnitTest")
      continue
    fi
    if [ ! -s "${classes_file}" ]; then
      if [ -f "${WORK_DIR}/${mid}.benchmark" ]; then
        echo "${m}: only benchmark tests map to the changes, and they're outside the default sweep; nothing to run"
        continue
      fi
      echo "${m}: running whole (no class-level mapping)"
      whole_batch+=("${m}:testDebugUnitTest")
      continue
    fi

    classes_sorted="$(sort -u "${classes_file}")"
    count="$(printf '%s\n' "${classes_sorted}" | grep -c .)"
    preview="$(printf '%s\n' "${classes_sorted}" | head -5 | paste -sd, -)"
    if [ "${count}" -gt 5 ]; then
      preview="${preview}, ..."
    fi
    echo "${m}: ${count} class(es): ${preview}"

    # --tests binds to the task before it, so every module's filter can share one invocation.
    run_tasks+=("${m}:testDebugUnitTest")
    while IFS= read -r cls_line; do
      [ -z "${cls_line}" ] && continue
      run_tasks+=(--tests "${cls_line}")
    done <<< "${classes_sorted}"
  done < <(sort -u "${WORK_DIR}/modules_seen")

  # One Gradle start for all modules instead of one each; --continue keeps a failing module from
  # hiding the others' results, as separate invocations did.
  if [ "${#run_tasks[@]}" -gt 0 ] || [ "${#whole_batch[@]}" -gt 0 ]; then
    print_and_run --continue ${run_tasks[@]+"${run_tasks[@]}"} ${whole_batch[@]+"${whole_batch[@]}"} || overall_rc=$?
  fi

  return "${overall_rc}"
}

run_changed_tests_mode() {
  local base="$1"
  local changed_files
  if ! changed_files="$(changed_files_since "${base}")"; then
    echo "error: cannot find a merge base with '${base}' (fetch it first?)" >&2
    exit 2
  fi

  if [ -z "${changed_files}" ]; then
    echo "No changes against ${base}; nothing to run."
    return 0
  fi

  # Build-system-wide changes affect any module's classpath or task graph;
  # class-level mapping doesn't apply, so fall back to the whole --changed run.
  local full_suite_trigger
  full_suite_trigger="$(build_system_trigger "${changed_files}")"
  if [ -n "${full_suite_trigger}" ]; then
    echo "Build-system file changed ('${full_suite_trigger}'); falling back to 'unit-test --changed'."
    run_changed_mode "${base}"
    return $?
  fi

  WORK_DIR="$(mktemp -d -t unit-test-changed-tests)"

  classify_changed_tests_files "${changed_files}"

  if [ ! -f "${WORK_DIR}/modules_seen" ]; then
    echo "No modules affected; nothing to run."
    return 0
  fi

  echo "Base: ${base}"
  echo "Changed files: $(printf '%s\n' "${changed_files}" | grep -c .)"
  echo

  run_changed_tests_batch
}
