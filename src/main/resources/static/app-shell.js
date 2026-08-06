(() => {
  const icons = {
    overview: '<svg viewBox="0 0 24 24"><path d="M12 3 4.5 6v5.4c0 4.6 3.1 7.8 7.5 9.6 4.4-1.8 7.5-5 7.5-9.6V6L12 3Z"/><path d="M8 10h8M12 7v8"/></svg>',
    matches: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m12 8 3 2.2-1.1 3.5h-3.8L9 10.2 12 8Zm-7.4 1.2L9 10.3m6 0 4.4-1.1M8.3 18l1.8-4.3m3.8 0 1.8 4.3"/></svg>',
    players: '<svg viewBox="0 0 24 24"><path d="m8 4-5 3 2 4 2-1v10h10V10l2 1 2-4-5-3a4.8 4.8 0 0 1-8 0Z"/></svg>',
    opponents: '<svg viewBox="0 0 24 24"><path d="M8.5 4 3 6.2v4c0 3.4 2.1 5.9 5.5 7.3 3.4-1.4 5.5-3.9 5.5-7.3v-4L8.5 4Z"/><path d="m15.5 6.5 5.5 2.2v4c0 3.4-2.1 5.9-5.5 7.3a10.5 10.5 0 0 1-4.2-2.7M6.5 10.5h4m-2-2v4m5.5-.5h3"/></svg>',
    settings: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></svg>'
  };
  const pages = {
    overview: { label: "Visão Geral", href: "/", icon: icons.overview },
    matches: { label: "Partidas", href: "/history", icon: icons.matches },
    players: { label: "Jogadores", href: "/players", icon: icons.players },
    // opponents: { label: "Adversários", href: "/opponents", icon: icons.opponents }, // Temporariamente desabilitado
  };

  function link(page, currentPage) {
    const item = pages[page];
    const isCurrent = currentPage === page;
    const sectionActive = page === "matches" && currentPage === "compare";
    return `
      <a class="app-shell-link" href="${item.href}"
         ${isCurrent ? 'aria-current="page"' : ""}
         ${sectionActive ? 'data-section-active="true"' : ""}>
        <span class="app-shell-icon" aria-hidden="true">${item.icon}</span>
        <span>${item.label}</span>
      </a>
    `;
  }

  function csrfCookie() {
    const cookie = document.cookie.split("; ").find((entry) => entry.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : null;
  }

  async function applicationFetch(input, init = {}) {
    const request = input instanceof Request ? input : null;
    const method = (init.method || request?.method || "GET").toUpperCase();
    const target = new URL(request?.url || input, window.location.href);
    const sameOrigin = target.origin === window.location.origin;
    const options = { ...init };

    if (sameOrigin) {
      options.credentials = init.credentials || "same-origin";
    }

    if (sameOrigin && !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
      const token = csrfCookie();
      if (token) {
        const headers = new Headers(request?.headers || undefined);
        new Headers(init.headers || undefined).forEach((value, name) => headers.set(name, value));
        headers.set("X-XSRF-TOKEN", token);
        options.headers = headers;
      }
    }
    return window.__nativeFetch(input, options);
  }

  window.__nativeFetch = window.fetch.bind(window);
  window.eafcFetch = applicationFetch;
  window.fetch = applicationFetch;

  function createShell(currentPage, content) {
    const shell = document.createElement("div");
    shell.className = "app-shell";
    shell.dataset.menuOpen = "false";
    shell.innerHTML = `
      <header class="app-shell-mobile-header">
        <a class="app-shell-mobile-brand" href="/">
          <span class="app-shell-mark" aria-hidden="true">FC</span>
          <span>EA FC STATS</span>
        </a>
        <button class="app-shell-menu-button" type="button"
                aria-label="Abrir navegação" aria-expanded="false">☰</button>
      </header>
      <aside class="app-shell-sidebar" aria-label="Navegação principal">
        <a class="app-shell-brand" href="/">
          <span class="app-shell-mark" aria-hidden="true">FC</span>
          <span class="app-shell-brand-copy">
            <span class="app-shell-brand-name">EA FC STATS</span>
            <span class="app-shell-brand-club">Associação BF</span>
          </span>
        </a>
        <nav class="app-shell-nav">
          <div class="app-shell-nav-label">Explorar</div>
          ${link("overview", currentPage)}
          ${link("matches", currentPage)}
          ${link("players", currentPage)}
        </nav>
        <!-- ${link("opponents", currentPage)} Temporariamente desabilitado -->
        <div class="app-shell-utility">
          <a class="app-shell-link" href="/settings"
             ${currentPage === "settings" ? 'aria-current="page"' : ""}>
            <span class="app-shell-icon" aria-hidden="true">${icons.settings}</span>
            <span>Configurações</span>
          </a>
        </div>
      </aside>
      <button class="app-shell-overlay" type="button" aria-label="Fechar navegação"></button>
      <div class="app-shell-main">
        <div class="app-shell-content"></div>
      </div>
    `;
    shell.querySelector(".app-shell-content").append(content);
    return shell;
  }

  function initialize() {
    const content = document.querySelector("[data-app-content]");
    const currentPage = document.body.dataset.appPage;
    if (!content || !currentPage) return;

    document.dispatchEvent(new CustomEvent("eafc:auth-ready", { detail: {} }));
    const shell = createShell(currentPage, content);
    document.body.prepend(shell);
    document.body.classList.add("app-shell-ready");

    const menuButton = shell.querySelector(".app-shell-menu-button");
    const overlay = shell.querySelector(".app-shell-overlay");

    const setMenuOpen = (open) => {
      shell.dataset.menuOpen = String(open);
      menuButton.setAttribute("aria-expanded", String(open));
      menuButton.setAttribute("aria-label", open ? "Fechar navegação" : "Abrir navegação");
    };

    menuButton.addEventListener("click", () => {
      setMenuOpen(shell.dataset.menuOpen !== "true");
    });
    overlay.addEventListener("click", () => setMenuOpen(false));
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") setMenuOpen(false);
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initialize);
  } else {
    initialize();
  }
})();
