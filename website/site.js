// shuttlemusicplayer.com: the theme toggle and the App Store switch. Every page works without it.
// Loaded in <head> without defer, so a saved theme applies before the first paint.

// Flip to true once Shuttle Music is live on the App Store. Until then the App Store badge shows
// "Coming soon to the App Store" and isn't a link. When true, every App Store badge links to the
// listing, iPhone Safari gets its Smart App Banner (apple-itunes-app), and the iOS app's structured
// data gains its download link. See server/README.md.
const APP_STORE_LIVE = false;
const APP_STORE_ID = "6818057709";
const APP_STORE_URL = "https://apps.apple.com/app/id" + APP_STORE_ID;

(function () {
  const root = document.documentElement;
  const systemDark = matchMedia("(prefers-color-scheme: dark)");

  function stored(value) {
    try {
      if (value === undefined) return localStorage.getItem("theme");
      if (value === null) localStorage.removeItem("theme");
      else localStorage.setItem("theme", value);
    } catch (e) {
      return null;
    }
  }

  const saved = stored();
  if (saved === "light" || saved === "dark") root.dataset.theme = saved;

  if (APP_STORE_LIVE) {
    const meta = document.createElement("meta");
    meta.name = "apple-itunes-app";
    meta.content = "app-id=" + APP_STORE_ID;
    document.head.appendChild(meta);
  }

  function effectiveTheme() {
    return root.dataset.theme || (systemDark.matches ? "dark" : "light");
  }

  // A <source media="(prefers-color-scheme: dark)"> only knows the system setting; make it follow the toggle too.
  function syncPictures() {
    const theme = root.dataset.theme;
    const media = theme === "dark" ? "all" : theme === "light" ? "not all" : "(prefers-color-scheme: dark)";
    document.querySelectorAll("source[data-dark]").forEach((source) => {
      source.media = media;
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    syncPictures();

    const toggle = document.querySelector(".theme-toggle");
    if (toggle) {
      const label = () =>
        toggle.setAttribute("aria-label", effectiveTheme() === "dark" ? "Switch to light theme" : "Switch to dark theme");
      toggle.hidden = false;
      label();
      toggle.addEventListener("click", () => {
        const next = effectiveTheme() === "dark" ? "light" : "dark";
        // Choosing what the system already shows goes back to following the system.
        if ((next === "dark") === systemDark.matches) {
          delete root.dataset.theme;
          stored(null);
        } else {
          root.dataset.theme = next;
          stored(next);
        }
        syncPictures();
        label();
      });
    }

    if (APP_STORE_LIVE) {
      document.querySelectorAll("[data-app-store]").forEach((soon) => {
        const link = document.createElement("a");
        link.href = APP_STORE_URL;
        link.rel = "noopener";
        const badge = soon.querySelector("img");
        badge.alt = "Download on the App Store";
        link.appendChild(badge);
        soon.replaceWith(link);
      });
      const ld = document.getElementById("ld-ios");
      if (ld) {
        const app = JSON.parse(ld.textContent);
        app.downloadUrl = APP_STORE_URL;
        ld.textContent = JSON.stringify(app);
      }
    }
  });
})();
