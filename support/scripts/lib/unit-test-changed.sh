# shellcheck shell=bash
# unit-test --changed. Sourced, not run; needs unit-test-modules.sh and the entry script's globals.

# Prints 1 when a Roborazzi verify is triggered: docs/design/** changed, a changed main source
# (src/main, src/commonMain, src/androidMain) in a module that applies the roborazzi plugin
# (non-composable code can change rendered text too, #830), or any changed file that currently
# contains a @Composable function. Prints 0 otherwise.
changed_roborazzi_trigger() {
  local changed_files="$1" roborazzi=0 f rdir rpath rel_f
  while IFS= read -r f; do
    [ -z "${f}" ] && continue
    case "${f}" in
      docs/design/*) roborazzi=1 ;;
      android/* | shared/*)
        if [ "${roborazzi}" -eq 0 ]; then
          IFS=$'\t' read -r rdir rpath <<< "$(module_dir_and_path_for_file "${f}")"
          rel_f="${f#"${rdir}"/}"
          case "${rel_f}" in
            src/main/* | src/commonMain/* | src/androidMain/*)
              [ -n "${rdir}" ] && module_has_roborazzi "${rpath}" && roborazzi=1
              ;;
          esac
        fi ;;
    esac
    if [ "${roborazzi}" -eq 0 ] && [ "${f%.kt}" != "${f}" ] && [ -f "${REPO_ROOT}/${f}" ]; then
      grep -q '@Composable' "${REPO_ROOT}/${f}" 2>/dev/null && roborazzi=1
    fi
  done <<< "${changed_files}"
  echo "${roborazzi}"
  return 0
}

# Classify each changed android/** or shared/** file against its module: a src/test
# file contributes its own class name as a filter candidate (only if it
# still exists — a deletion contributes nothing); a main file contributes
# its corresponding *Test class only if exactly one exists, otherwise (and
# for any non-.kt change, or an androidTest change) the module falls back
# to a whole run. A main-source or build-file change in a module the :android:app tests run
# against (app_tests_cover_module; the rest are skipped) marks :android:app as downstream-affected.
# A file under android/** or shared/** that isn't part of any registered module is skipped with
# a printed note. Records its findings as files under WORK_DIR.
classify_changed_files() {
  local changed_files="$1" f mdir mpath id rel cls matches match_count
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
      src/test/*.kt | src/commonTest/*.kt | src/androidHostTest/*.kt)
        # Only filter on classes that still exist; a deleted test file must
        # not add a --tests filter for a class Gradle can no longer find.
        # A helper (a Robot, Scenarios, a fake) has no @Test methods, and
        # filtering on it fails with "No tests found": run the module whole.
        cls="$(basename "${rel}" .kt)"
        if [ "${cls%Test}" = "${cls}" ]; then
          echo "note: '${f}' is a test helper; running ${mpath} whole"
          touch "${WORK_DIR}/${id}.unmappable"
        elif [ -f "${REPO_ROOT}/${f}" ]; then
          echo "${cls}" >> "${WORK_DIR}/${id}.classes"
        fi
        ;;
      src/androidTest/*.kt)
        # androidTest classes aren't part of testDebugUnitTest; filtering on
        # one fails with "No tests found", so fall back to a whole run.
        echo "note: '${f}' is an androidTest source; running ${mpath} whole"
        touch "${WORK_DIR}/${id}.unmappable"
        ;;
      src/main/*.kt | src/commonMain/*.kt | src/androidMain/*.kt | src/iosMain/*.kt)
        cls="$(basename "${rel}" .kt)"
        matches="$(find "${REPO_ROOT}/${mdir}/src/test" "${REPO_ROOT}/${mdir}/src/commonTest" "${REPO_ROOT}/${mdir}/src/androidHostTest" "${REPO_ROOT}/${mdir}/src/androidTest" \
          -type f -name "${cls}Test.kt" 2>/dev/null || true)"
        match_count="$(printf '%s\n' "${matches}" | grep -c . || true)"
        if [ "${match_count}" -eq 1 ]; then
          echo "${cls}Test" >> "${WORK_DIR}/${id}.classes"
        else
          touch "${WORK_DIR}/${id}.unmappable"
        fi
        touch "${WORK_DIR}/${id}.main"
        app_tests_cover_module "${mpath}" && touch "${WORK_DIR}/app_downstream_triggered"
        ;;
      src/test/*|src/commonTest/*|src/androidHostTest/*|src/androidTest/*)
        # Non-.kt test-source change (e.g. resources): run the module whole.
        touch "${WORK_DIR}/${id}.unmappable"
        ;;
      *)
        # Non-.kt, non-test change (build.gradle.kts, manifest, resources):
        # run the module whole, and treat it like a main-source change.
        touch "${WORK_DIR}/${id}.unmappable"
        touch "${WORK_DIR}/${id}.main"
        app_tests_cover_module "${mpath}" && touch "${WORK_DIR}/app_downstream_triggered"
        ;;
    esac
  done <<< "${changed_files}"
  return 0
}

# Full suite for a build-system-wide change. One invocation: a module's verifyRoborazziDebug in
# the task graph switches that module's testDebugUnitTest to verify mode, so its suite runs once
# rather than twice (#552). Args: <trigger file> <roborazzi 0|1>.
run_full_suite() {
  local trigger="$1" roborazzi="$2" full_tasks=(testDebugUnitTest) rm full_rc=0
  echo "Build-system file changed ('${trigger}'); running the full test suite."
  if [ "${roborazzi}" -eq 1 ]; then
    while IFS= read -r rm; do
      [ -n "${rm}" ] && full_tasks+=("${rm}:verifyRoborazziDebug")
    done < <(roborazzi_module_paths)
  fi
  print_and_run "${full_tasks[@]}" || full_rc=$?
  return "${full_rc}"
}

# Step 1: compile unit-test sources for every affected module first; stop on a compile error so
# it doesn't surface mid test-run. Args: the modules to compile.
compile_changed_modules() {
  [ "$#" -gt 0 ] || return 0
  local compile_tasks=() cm
  for cm in "$@"; do
    compile_tasks+=("${cm}:$(compile_test_task_for "${cm}")")
  done
  if ! print_and_run "${compile_tasks[@]}"; then
    echo "Compile failed; stopping before running tests." >&2
    return 1
  fi
  return 0
}

# Step 2: run tests: batch every whole-module run into one invocation (safe, since none of them
# pass --tests), and run each filtered module in its own invocation so its --tests filter can't
# shadow another module's tests in the same build. Args: <app path> <app downstream 0|1>
# <roborazzi 0|1> <modules...>.
run_changed_modules() {
  local app_path="$1" app_downstream="$2" roborazzi="$3"
  shift 3
  local overall_rc=0 whole_batch=() m mid unmappable_file classes_file app_robo_placed=0
  for m in "${@:-}"; do
    [ -z "${m}" ] && continue
    mid="$(module_id "${m}")"
    unmappable_file="${WORK_DIR}/${mid}.unmappable"
    classes_file="${WORK_DIR}/${mid}.classes"

    local whole=0
    if [ "${m}" = "${app_path}" ] && [ "${app_downstream}" -eq 1 ]; then
      whole=1
    elif [ -f "${unmappable_file}" ] || [ ! -s "${classes_file}" ]; then
      whole=1
    fi

    if [ "${whole}" -eq 1 ]; then
      whole_batch+=("${m}:testDebugUnitTest")
      # Gated on the trigger, not on "app already placed": every changed
      # screenshot module verifies its own baselines regardless of order.
      if [ "${roborazzi}" -ne 0 ] && module_has_roborazzi "${m}"; then
        whole_batch+=("${m}:verifyRoborazziDebug")
        [ "${m}" = "${app_path}" ] && app_robo_placed=1
      fi
    else
      local filter_args=() cls_line
      while IFS= read -r cls_line; do
        [ -z "${cls_line}" ] && continue
        filter_args+=(--tests "${cls_line}")
      done < <(sort -u "${classes_file}")
      local run_tasks=("${m}:testDebugUnitTest")
      if [ "${roborazzi}" -ne 0 ] && module_has_roborazzi "${m}"; then
        run_tasks+=("${m}:verifyRoborazziDebug")
        [ "${m}" = "${app_path}" ] && app_robo_placed=1
      fi
      print_and_run "${run_tasks[@]}" "${filter_args[@]}" || overall_rc=$?
    fi
  done

  if [ "${#whole_batch[@]}" -gt 0 ]; then
    print_and_run "${whole_batch[@]}" || overall_rc=$?
  fi

  # app_robo_placed still 0 here means :android:app wasn't otherwise scheduled
  # (e.g. a docs/design-only change): run just the verify task.
  if [ "${roborazzi}" -ne 0 ] && [ "${app_robo_placed}" -eq 0 ]; then
    print_and_run "${app_path}:verifyRoborazziDebug" || overall_rc=$?
  fi

  return "${overall_rc}"
}

run_changed_mode() {
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

  WORK_DIR="$(mktemp -d -t unit-test-changed)"

  local roborazzi
  roborazzi="$(changed_roborazzi_trigger "${changed_files}")"

  classify_changed_files "${changed_files}"

  # Build-system-wide changes can affect any module's classpath or task graph; per-module
  # mapping doesn't apply, so fall back to the full suite.
  local full_suite_trigger
  full_suite_trigger="$(build_system_trigger "${changed_files}")"
  if [ -n "${full_suite_trigger}" ]; then
    run_full_suite "${full_suite_trigger}" "${roborazzi}"
    return $?
  fi

  if [ ! -f "${WORK_DIR}/modules_seen" ] && [ "${roborazzi}" -eq 0 ]; then
    echo "No modules affected; nothing to run."
    return 0
  fi

  local app_path=":android:app"
  local app_downstream=0
  [ -f "${WORK_DIR}/app_downstream_triggered" ] && app_downstream=1

  local modules=() mpath
  if [ -f "${WORK_DIR}/modules_seen" ]; then
    while IFS= read -r mpath; do
      modules+=("${mpath}")
    done < <(sort -u "${WORK_DIR}/modules_seen")
  fi

  local app_in_modules=0 m
  for m in "${modules[@]:-}"; do
    [ "${m}" = "${app_path}" ] && app_in_modules=1
  done

  # :android:app runs a normal (filtered-or-whole) invocation if it changed
  # directly or was pulled in downstream; roborazzi alone only adds the
  # verifyRoborazziDebug task, not the whole app suite.
  local app_test_needed=0
  [ "${app_in_modules}" -eq 1 ] || [ "${app_downstream}" -eq 1 ] && app_test_needed=1
  if [ "${app_test_needed}" -eq 1 ] && [ "${app_in_modules}" -eq 0 ]; then
    modules+=("${app_path}")
  fi

  local compile_modules=()
  if [ "${#modules[@]}" -gt 0 ]; then compile_modules=("${modules[@]}"); fi
  if [ "${roborazzi}" -eq 1 ] && [ "${app_test_needed}" -eq 0 ]; then
    compile_modules+=("${app_path}")
  fi

  echo "Base: ${base}"
  echo "Changed files: $(printf '%s\n' "${changed_files}" | grep -c .)"
  echo "Affected modules: ${modules[*]:-(none)}"
  if [ "${roborazzi}" -eq 1 ]; then
    # App's screenshots render the other modules' components, so a trigger
    # always verifies app; each changed screenshot module verifies its own
    # (#831).
    local robo_verify="${app_path}" m2
    for m2 in "${modules[@]:-}"; do
      [ -n "${m2}" ] && [ "${m2}" != "${app_path}" ] && module_has_roborazzi "${m2}" && robo_verify="${robo_verify} ${m2}"
    done
    echo "Roborazzi: verifyRoborazziDebug will run for: ${robo_verify} (docs/design, @Composable or screenshot-module source change)"
  fi
  echo

  compile_changed_modules ${compile_modules[@]+"${compile_modules[@]}"} || return 1
  run_changed_modules "${app_path}" "${app_downstream}" "${roborazzi}" "${modules[@]:-}"
}
