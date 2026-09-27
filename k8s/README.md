# Shuttle2 k8s builders

Two flows, same Gradle tasks:

| Flow | Needs on this host | Cluster needs | Use when |
|---|---|---|---|
| `scripts/build-remote.sh` | `kubectl` + tar only | internet, `local-path` storage | remote k3s without docker (current setup) |
| `Dockerfile` + image | docker (or colima) | image visible to k3s | iterating often; SDK baked, no bootstrap |

## build-remote.sh (current default)

```bash
# debug APK (first run bootstraps SDK ~500MB + Gradle dist into the PVC cache)
./k8s/scripts/build-remote.sh --task :android:app:assembleDebug

# reuse workspace already on the PVC (skip the tar+cp)
./k8s/scripts/build-remote.sh --task :android:app:assembleDebug --no-sync

# unit tests / lint
./k8s/scripts/build-remote.sh --task :android:app:testDebugUnitTest
./k8s/scripts/build-remote.sh --task :android:app:lintDebug
```

State: namespace `shuttle-build`, PVC `shuttle-build-cache` (25Gi, `local-path`)
holding `sdk/`, `gradle/`, `workspace/`. Override via `k8s/config.env`
(`KUBE_CONTEXT`, `NAMESPACE`, `CACHE_SIZE`, task, CPU/mem). APKs land back in
`android/app/build/outputs/`; full log in `k8s/.cache-remote/last-build.log`.

Ported from OffBookPlus `k8s/` (colima+docker+rsync flow), adapted for a
remote cluster: no colima (`ensure_cluster` dropped), no rsync (tar +
`kubectl cp` via a seed pod), no prebuilt image (SDK bootstraps at runtime
from `eclipse-temurin:21-jdk-noble` — internet confirmed from the cluster).

## Dockerfile

For docker-capable hosts: `docker build -f k8s/Dockerfile -t
localhost/shuttle-android-builder:jdk21 .` then run the build Job against it
(skips the ~5min SDK bootstrap). Same SDK set as OffBookPlus (API
37.0+37.1, build-tools 37.0.0, JDK 21) matching `compileSdk 37`.
