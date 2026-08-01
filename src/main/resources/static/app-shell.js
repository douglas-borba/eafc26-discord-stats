(() => {
  const icons = {
    overview: '<svg viewBox="0 0 24 24"><path d="M12 3 4.5 6v5.4c0 4.6 3.1 7.8 7.5 9.6 4.4-1.8 7.5-5 7.5-9.6V6L12 3Z"/><path d="M8 10h8M12 7v8"/></svg>',
    matches: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m12 8 3 2.2-1.1 3.5h-3.8L9 10.2 12 8Zm-7.4 1.2L9 10.3m6 0 4.4-1.1M8.3 18l1.8-4.3m3.8 0 1.8 4.3"/></svg>',
    players: '<svg viewBox="0 0 24 24"><path d="m8 4-5 3 2 4 2-1v10h10V10l2 1 2-4-5-3a4.8 4.8 0 0 1-8 0Z"/></svg>',
    clubHistory: '<svg viewBox="0 0 24 24"><path d="M8 4h8v4a4 4 0 0 1-8 0V4Z"/><path d="M8 6H5v2a4 4 0 0 0 4 4m7-6h3v2a4 4 0 0 1-4 4m-3 0v5m-4 3h8m-6-3h4"/></svg>',
    settings: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></svg>'
  };
  const pages = {
    overview: { label: "Visão Geral", href: "/", icon: icons.overview },
    matches: { label: "Partidas", href: "/history", icon: icons.matches },
    players: { label: "Jogadores", href: "/players", icon: icons.players },
    clubHistory: { label: "História do Clube", href: "/insights", icon: icons.clubHistory },
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
          ${link("clubHistory", currentPage)}
        </nav>
        <p class="app-shell-story">Partidas viram histórias. Histórias revelam a trajetória do clube.</p>
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
