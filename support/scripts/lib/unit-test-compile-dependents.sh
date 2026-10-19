# shellcheck shell=bash
# unit-test --compile-dependents. Sourced, not run.

# Gradle paths a module depends on directly, read from project(":x"), project(':x') or project(path: ':x') in its build file.
module_project_deps() {
  local build_file
  build_file="$(module_build_file "$1")"
  [ -n "${build_file}" ] || return 0
  grep -Eo "project\((path: *)?[\"'](:[^\"']+)[\"']" "${build_file}" | sed -E "s/^project\((path: *)?[\"']//; s/[\"']$//" | sort -u
}

# Transitive dependents (excluding the inputs) of the Gradle paths given as arguments, one per
# line. A module that mentions a path in any configuration (even testImplementation) counts: an
# over-approximation that is cheap to compile.
dependents_of() {
  local seen all m d changed=1
  seen="$(printf '%s\n' "$@")"
  all="$(all_module_paths)"
  while [ "${changed}" -eq 1 ]; do
    changed=0
    while IFS= read -r m; do
      printf '%s\n' "${seen}" | grep -Fxq -- "${m}" && continue
      while IFS= read -r d; do
        [ -n "${d}" ] || continue
        if printf '%s\n' "${seen}" | grep -Fxq -- "${d}"; then
          seen="${seen}"$'\n'"${m}"
          printf '%s\n' "${m}"
          changed=1
          break
        fi
      done < <(module_project_deps "${m}")
    done <<< "${all}"
  done
}

# --compile-dependents: when a non-test Kotlin file under src/main, src/commonMain or
# src/androidMain changed in :android:domain, :shared or :android:core (the modules whose public
# interfaces other modules implement, #826), or in any module's commonMain, compile (not run) the
# test sources of every module that depends on it, so a fake that no longer implements a changed
# interface fails here rather than later. The changed module itself is covered by --changed.
run_compile_dependents_mode() {
  local base="$1" changed_files f mdp mdir mpath roots="" tasks=() m
  if ! changed_files="$(changed_files_since "${base}")"; then
    echo "error: cannot find a merge base with '${base}' (fetch it first?)" >&2
    exit 2
  fi
  while IFS= read -r f; do
    case "${f}" in
      *.kt) ;;
      *) continue ;;
    esac
    mdp="$(module_dir_and_path_for_file "${f}")" || true
    [ -n "${mdp}" ] || continue
    mdir="${mdp%%$'\t'*}"
    mpath="${mdp#*$'\t'}"
    case "${f#"${mdir}"/}" in
      src/main/*|src/commonMain/*|src/androidMain/*) ;;
      *) continue ;;
    esac
    case "${mpath}" in
      :android:domain|:shared|:android:core) ;;
      *) case "${f#"${mdir}"/}" in src/commonMain/*) ;; *) continue ;; esac ;;
    esac
    printf '%s\n' "${roots}" | grep -Fxq -- "${mpath}" || roots="${roots}${roots:+$'\n'}${mpath}"
  done <<< "${changed_files}"
  if [ -z "${roots}" ]; then
    echo "No shared-interface sources changed against ${base}; no dependent test compile needed."
    return 0
  fi
  echo "Shared modules changed: $(printf '%s' "${roots}" | tr '\n' ' ')"
  # shellcheck disable=SC2086 # roots is newline-separated; one path per word
  while IFS= read -r m; do
    [ -n "${m}" ] || continue
    tasks+=("${m}:$(compile_test_task_for "${m}")")
  done < <(dependents_of ${roots})
  if [ "${#tasks[@]}" -eq 0 ]; then
    echo "No dependent modules."
    return 0
  fi
  print_and_run "${tasks[@]}"
}
