(() => {
  const listElement = document.getElementById("match-list");
  const detailElement = document.getElementById("match-detail");
  const metaElement = document.getElementById("history-meta");
  const layoutElement = document.getElementById("matches-layout");
  const editorialDesign = window.EafcEditorialDesign;
  let selectedMatchId = null;
  const requestedMatchId = new URLSearchParams(window.location.search).get("matchId");

  const escapeHtml = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

  const display = (value) => value === null || value === undefined ? "—" : escapeHtml(value);
  const ratio = (completed, attempted) =>
    completed === null || completed === undefined || attempted === null || attempted === undefined
      ? "—"
      : `${escapeHtml(completed)}/${escapeHtml(attempted)}`;

  const awardVoice = editorialDesign.characters;

  function sectionHeading(kicker, title, question) {
    return `
      <header class="editorial-heading">
        <span class="editorial-kicker">${escapeHtml(kicker)}</span>
        <h2>${escapeHtml(title)}</h2>
        <p>${escapeHtml(question)}</p>
      </header>
    `;
  }

  function renderFacts(facts) {
    if (!facts || !facts.length) return "";
    return `<ul class="fact-list">${facts.map(fact => `
      <li><span>${escapeHtml(fact.label)}</span><strong>${escapeHtml(fact.value)}</strong></li>
    `).join("")}</ul>`;
  }

  function renderList(matches, metadata) {
    metaElement.textContent =
      `${metadata.matchCount} partida${metadata.matchCount === 1 ? "" : "s"} registrada${metadata.matchCount === 1 ? "" : "s"}`;

    if (!matches.length) {
      listElement.innerHTML =
        '<div class="matches-state empty-editorial-state"><div><span class="matches-state-icon" aria-hidden="true">📭</span>Nenhuma partida registrada ainda.</div></div>';
      detailElement.className = "matches-state empty-editorial-state";
      detailElement.innerHTML =
        '<div><span class="matches-state-icon" aria-hidden="true">🗂️</span>As histórias aparecerão aqui depois da primeira partida processada.</div>';
      return;
    }

    listElement.innerHTML = matches.map(match => `
      <button class="match-list-item" type="button" data-match-id="${escapeHtml(match.matchId)}">
        <div class="match-list-top">
          <span>${escapeHtml(match.dateLabel)}</span>
          <span class="outcome-badge ${escapeHtml(match.outcome.code)}">
            ${escapeHtml(match.outcome.icon)} ${escapeHtml(match.outcome.label)}
          </span>
        </div>
        <div class="match-list-scoreline">
          <div class="match-list-clubs">
            <div class="match-list-club">${escapeHtml(match.ourClub.name)}</div>
            <div class="match-list-club">${escapeHtml(match.opponentClub.name)}</div>
          </div>
          <div class="match-list-score">${match.ourClub.score} × ${match.opponentClub.score}</div>
        </div>
        ${match.competition ? `<div class="matches-meta">${escapeHtml(match.competition)}</div>` : ""}
      </button>
    `).join("");

    listElement.querySelectorAll(".match-list-item").forEach(button => {
      button.addEventListener("click", () => openMatch(button.dataset.matchId, true));
    });

    const requestedExists = matches.some(match => match.matchId === requestedMatchId);
    if (requestedExists || !window.matchMedia("(max-width: 760px)").matches) {
      openMatch(requestedExists ? requestedMatchId : matches[0].matchId);
    }
  }

  async function openMatch(matchId, updateLocation = false) {
    selectedMatchId = matchId;
    listElement.querySelectorAll(".match-list-item").forEach(item => {
      item.classList.toggle("active", item.dataset.matchId === matchId);
    });
    layoutElement.classList.add("is-viewing-match");
    if (updateLocation) {
      const url = new URL(window.location.href);
      url.searchParams.set("matchId", matchId);
      window.history.pushState({ matchId }, "", url);
    }
    detailElement.className = "matches-state loading-editorial-state";
    detailElement.innerHTML =
      '<div><span class="matches-state-icon" aria-hidden="true">⏳</span>Carregando a história da partida…</div>';

    try {
      const response = await fetch(`/api/history/matches/${encodeURIComponent(matchId)}`);
      const payload = await response.json();
      if (!response.ok || payload.status !== "success") {
        throw new Error(payload.message || "Não foi possível recuperar a partida.");
      }
      if (selectedMatchId === matchId) renderDetail(payload.match);
    } catch (error) {
      detailElement.className = "matches-state matches-error";
      detailElement.innerHTML =
        `<div><span class="matches-state-icon" aria-hidden="true">⚠️</span>${escapeHtml(error.message)}</div>`;
    }
  }

  function renderAward(award) {
    const voice = awardVoice[award.type] || {
      label: award.label,
      icon: award.icon,
      message: "Um reconhecimento produzido a partir dos fatos desta partida.",
    };
    const winner = award.awarded ? award.winnerName || award.winnerId : "Não concedido";
    const fact = award.awarded
      ? voice.fact || award.reason
      : "Nenhum jogador atendeu aos critérios desta categoria.";

    return `
      <article class="character-card character-card--${escapeHtml(voice.tone || "neutral")}" data-awarded="${award.awarded}">
        <div class="character-card__identity">
          <span class="character-card__icon" aria-hidden="true">${escapeHtml(voice.icon)}</span>
          <span class="editorial-card-label">${escapeHtml(voice.label)}</span>
        </div>
        <h3>${escapeHtml(winner)}</h3>
        <p class="editorial-fact">${escapeHtml(fact)}</p>
        ${renderFacts(award.facts)}
        ${award.awarded ? `<p class="editorial-message">${escapeHtml(voice.message)}</p>` : ""}
      </article>
    `;
  }

  function storyVoice(story) {
    if (story.type === "OFFENSIVE_NARRATIVE") {
      const reading = story.facts.find(fact => fact.label === "Leitura")?.value;
      if (reading === "Decisivo") {
        return editorialDesign.stories.DECISIVE;
      }
      if (reading === "Ameaça constante") {
        return editorialDesign.stories.CONSTANT_THREAT;
      }
      return {
        ...editorialDesign.stories.NEAR_MISS,
        fact: `A participação ofensiva recebeu a leitura “${reading || "oportunidades não convertidas"}”.`,
      };
    }

    return editorialDesign.stories[story.type] || {
      ...editorialDesign.stories.DEFAULT,
      title: story.title,
    };
  }

  function renderStory(story) {
    const voice = storyVoice(story);
    return `
      <article class="story-chapter story-chapter--${escapeHtml(voice.tone)}">
        <div class="story-chapter__identity">
          <span class="story-chapter__icon" aria-hidden="true">${escapeHtml(voice.icon)}</span>
          <span class="editorial-card-label">Capítulo da partida</span>
        </div>
        <h3>${escapeHtml(voice.title)}</h3>
        ${story.involvedPlayers.length
          ? `<div class="story-chapter__players">${story.involvedPlayers.map(escapeHtml).join(", ")}</div>`
          : ""}
        <p class="editorial-fact">${escapeHtml(voice.fact)}</p>
        ${renderFacts(story.facts)}
        <p class="editorial-message">${escapeHtml(voice.message)}</p>
      </article>
    `;
  }

  function renderContributions(stories) {
    const contributions = stories.filter(story => story.type === "GOALS" || story.type === "ASSISTS");
    if (!contributions.length) return "";
    return `
      <div class="contribution-block">
        ${contributions.map(story => `
          <article class="contribution-card">
            <strong>${story.type === "GOALS" ? "⚽ Participações em gols" : "🎯 Assistências"}</strong>
            ${renderFacts(story.facts)}
          </article>
        `).join("")}
      </div>
    `;
  }

  function renderCollective(match) {
    const highlights = match.stories.find(story => story.type === "HIGHLIGHTS");
    const teamAverage = highlights?.facts.find(fact => fact.label === "Média do time");
    const metrics = [
      ["Gols marcados", match.summary.ourClub.score],
      ["Gols sofridos", match.summary.opponentClub.score],
    ];
    if (teamAverage) metrics.push(["Média do time", teamAverage.value]);

    const highlightFacts = highlights?.facts.filter(fact => fact.label !== "Média do time") || [];
    return `
      <div class="metric-strip">
        ${metrics.map(([label, value]) => `
          <div class="metric-strip__item"><strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span></div>
        `).join("")}
      </div>
      ${highlightFacts.length ? `
        <article class="collective-highlights">
          <div class="editorial-card-label">Destaques por nota</div>
          ${renderFacts(highlightFacts)}
        </article>
      ` : ""}
    `;
  }

  function renderPlayers(players) {
    // Sort players: active players by rating DESC, then disconnected players (3.0) at the end
    const disconnectedPlayers = players.filter(p => p.rating === 3.0);
    const activePlayers = players.filter(p => p.rating !== 3.0);
    activePlayers.sort((a, b) => (b.rating ?? 0) - (a.rating ?? 0));
    const sortedPlayers = [...activePlayers, ...disconnectedPlayers];

    const tableRows = sortedPlayers.map(player => {
      const isDisconnected = player.rating === 3.0;
      return `
      <tr>
        <td>
          <a href="/players?playerId=${encodeURIComponent(player.id)}&from=match-history"><strong>${escapeHtml(player.name)}</strong></a>
          <span class="player-role">${escapeHtml(player.role)}</span>
          ${isDisconnected ? '<span class="player-disconnected">Saiu da partida</span>' : ''}
        </td>
        <td>${display(player.rating)}</td>
        <td>${display(player.goals)}</td>
        <td>${display(player.assists)}</td>
        <td>${display(player.shots)}</td>
        <td>${ratio(player.passesCompleted, player.passesAttempted)}</td>
        <td>${ratio(player.tacklesCompleted, player.tacklesAttempted)}</td>
        <td>${display(player.redCards)}</td>
        <td>${display(player.saves)}</td>
      </tr>
    `;
    }).join("");

    const mobileCards = sortedPlayers.map(player => {
      const isDisconnected = player.rating === 3.0;
      const stats = [
        `${display(player.goals)} G`,
        `${display(player.assists)} A`,
        `${display(player.shots)} finalizações`,
        `Passes ${ratio(player.passesCompleted, player.passesAttempted)}`,
        `Desarmes ${ratio(player.tacklesCompleted, player.tacklesAttempted)}`,
      ];
      if (player.redCards) stats.push(`${display(player.redCards)} cartão vermelho`);
      if (player.saves !== null && player.saves !== undefined) stats.push(`${display(player.saves)} defesas`);
      return `
        <article class="player-performance-card player-mobile-card">
          <div class="player-mobile-head">
            <div>
              <a href="/players?playerId=${encodeURIComponent(player.id)}&from=match-history"><strong>${escapeHtml(player.name)}</strong></a>
              <span class="player-role">${escapeHtml(player.role)}</span>
              ${isDisconnected ? '<span class="player-disconnected-mobile">Saiu da partida</span>' : ''}
            </div>
            <span class="player-mobile-rating">${display(player.rating)}</span>
          </div>
          <div class="player-mobile-stats">${stats.join(" · ")}</div>
        </article>
      `;
    }).join("");

    return `
      <div class="players-table-wrap">
        <table class="player-performance-table">
          <thead>
            <tr><th>Jogador</th><th>Nota</th><th>G</th><th>A</th><th>Fin.</th><th>Passes</th><th>Desarmes</th><th>CV</th><th>Def.</th></tr>
          </thead>
          <tbody>${tableRows}</tbody>
        </table>
      </div>
      <div class="players-mobile">${mobileCards}</div>
    `;
  }

  function renderEvidence(match) {
    const awardEntries = match.awards.map(award => {
      const voice = awardVoice[award.type] || { label: award.label };
      return `
        <div class="evidence-entry">
          <strong>${escapeHtml(voice.label)}</strong><br>
          ${escapeHtml(award.reason)}
          ${renderFacts(award.facts)}
          <div class="technical-key">Regras: ${award.ruleIds.map(escapeHtml).join(", ")}</div>
        </div>
      `;
    }).join("");

    const storyEntries = match.stories.map(story => `
      <div class="evidence-entry">
        <strong>${escapeHtml(storyVoice(story).title)}</strong><br>
        Prioridade: ${escapeHtml(story.priority)} · ${story.evidenceCount} evidência${story.evidenceCount === 1 ? "" : "s"}
        <div class="technical-key">Regras: ${story.ruleIds.map(escapeHtml).join(", ")}</div>
      </div>
    `).join("");

    const eligibilityEntries = match.players.map(player => `
      <div class="evidence-entry">
        <strong>${escapeHtml(player.name)}</strong> · Elegível: ${display(player.eligibility)}
        ${player.durationSeconds !== null && player.durationSeconds !== undefined
          ? ` · ${escapeHtml(player.durationSeconds)} segundos`
          : ""}
      </div>
    `).join("");

    return `
      <div class="evidence-stack">
        <details class="evidence-disclosure">
          <summary>Premiações</summary>
          <div class="evidence-body">${awardEntries}</div>
        </details>
        <details class="evidence-disclosure">
          <summary>Histórias e regras aplicadas</summary>
          <div class="evidence-body">${storyEntries}</div>
        </details>
        <details class="evidence-disclosure">
          <summary>Elegibilidade dos jogadores</summary>
          <div class="evidence-body">${eligibilityEntries}</div>
        </details>
        <details class="evidence-disclosure">
          <summary>Proveniência canônica</summary>
          <div class="evidence-body">
            Schema ${match.provenance.schemaVersion} · Engine ${escapeHtml(match.provenance.engineVersion)}
            <div class="technical-key">MatchId: ${escapeHtml(match.summary.matchId)}</div>
            <div class="technical-key">Gerado em: ${escapeHtml(match.provenance.generatedAt)}</div>
          </div>
        </details>
      </div>
    `;
  }

  function renderDetail(match) {
    const summary = match.summary;
    const narrativeStories = match.stories.filter(story =>
      ["OFFENSIVE_NARRATIVE", "RED_CARD", "PASS_PRECISION", "LOST_MAIL", "GOALKEEPER"].includes(story.type)
    );

    detailElement.className = "";
    detailElement.innerHTML = `
      <article class="match-article">
        <button class="mobile-archive-button" type="button">← Todas as partidas</button>

        <header class="hero-section match-hero">
          <div class="match-hero-label">
            <span class="editorial-kicker">O que aconteceu</span>
            <h2>A Partida</h2>
          </div>
          <div class="match-hero-meta">
            <span>${escapeHtml(summary.dateLabel)}${summary.competition ? ` · ${escapeHtml(summary.competition)}` : ""}</span>
            <span class="outcome-badge ${escapeHtml(summary.outcome.code)}">
              ${escapeHtml(summary.outcome.icon)} ${escapeHtml(summary.outcome.label)}
            </span>
          </div>
          <div class="match-hero-score">
            <div class="match-hero-club">${escapeHtml(summary.ourClub.name)}</div>
            <strong>${summary.ourClub.score} × ${summary.opponentClub.score}</strong>
            <span class="match-hero-club">${escapeHtml(summary.opponentClub.name)}</span>
          </div>
        </header>

        <section class="editorial-section" aria-labelledby="characters-title">
          ${sectionHeading("Quem marcou o jogo", "Personagens da Partida", "Quem foram os protagonistas desta atuação?")}
          <div class="protagonist-grid" id="characters-title">
            ${match.awards.map(renderAward).join("")}
          </div>
          ${renderContributions(match.stories)}
        </section>

        <section class="editorial-section" aria-labelledby="stories-title">
          ${sectionHeading("Como aconteceu", "A História do Jogo", "Quais decisões ajudam a explicar esta partida?")}
          <div class="story-grid" id="stories-title">
            ${narrativeStories.length
              ? narrativeStories.map(renderStory).join("")
              : '<div class="empty-editorial-state empty-editorial-state--compact">Nenhuma narrativa especial foi identificada nesta partida.</div>'}
          </div>
        </section>

        <section class="editorial-section" aria-labelledby="team-title">
          ${sectionHeading("Os números do coletivo", "O Time em Números", "Quais fatos sustentam a leitura da equipe?")}
          <div id="team-title">${renderCollective(match)}</div>
        </section>

        <section class="editorial-section" aria-labelledby="players-title">
          ${sectionHeading("Atuação por atuação", "Jogador por Jogador", "Como cada jogador participou deste capítulo?")}
          <div id="players-title">${renderPlayers(match.players)}</div>
        </section>

        <section class="editorial-section" aria-labelledby="evidence-title">
          ${sectionHeading("Auditoria sob demanda", "Critérios e Evidências", "Como o sistema chegou a estas conclusões?")}
          <div id="evidence-title">${renderEvidence(match)}</div>
        </section>
      </article>
    `;

    detailElement.querySelector(".mobile-archive-button").addEventListener("click", () => {
      layoutElement.classList.remove("is-viewing-match");
      document.querySelector(".matches-page-header").scrollIntoView();
    });
  }

  async function loadMatches() {
    try {
      const response = await fetch("/api/history/matches");
      if (!response.ok) throw new Error("Não foi possível carregar as partidas.");
      const payload = await response.json();
      renderList(payload.matches || [], payload.metadata);
    } catch (error) {
      metaElement.textContent = "Partidas indisponíveis";
      listElement.innerHTML =
        `<div class="matches-state matches-error"><div><span class="matches-state-icon" aria-hidden="true">⚠️</span>${escapeHtml(error.message)}</div></div>`;
    }
  }

  loadMatches();
})();
