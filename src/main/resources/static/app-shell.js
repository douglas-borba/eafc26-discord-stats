(() => {
  const pages = {
    overview: { label: "Visão Geral", href: "/", icon: "⌂" },
    matches: { label: "Partidas", href: "/history", icon: "⚽" },
    players: { label: "Jogadores", href: "/players", icon: "♟" },
    clubHistory: { label: "História do Clube", href: "/insights", icon: "◆" },
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
          <div class="app-shell-subnav">
            <a class="app-shell-sublink" href="/compare"
               ${currentPage === "compare" ? 'aria-current="page"' : ""}>Comparar partidas</a>
          </div>
          ${link("players", currentPage)}
          ${link("clubHistory", currentPage)}
        </nav>
        <p class="app-shell-story">Partidas viram histórias. Histórias revelam a trajetória do clube.</p>
        <div class="app-shell-utility">
          <a class="app-shell-link" href="/settings"
             ${currentPage === "settings" ? 'aria-current="page"' : ""}>
            <span class="app-shell-icon" aria-hidden="true">⚙</span>
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
