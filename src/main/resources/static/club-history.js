(() => {
  const root = document.getElementById("memorial-content");
  const scope = document.getElementById("memorial-scope");
  const esc = value => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
  const priorities = {
    lead: ["BIGGEST_WIN", "FIRST_WIN", "BEST_TEAM_AVERAGE", "TOP_SCORER", "ASSIST_LEADER", "LONGEST_UNBEATEN_STREAK"],
    people: ["TOP_SCORER", "ASSIST_LEADER", "MOST_CRAQUES", "MOST_XERIFES", "HIGHEST_PLAYER_AVERAGE", "MOST_BAGRES"],
    matches: ["BIGGEST_WIN", "FIRST_WIN", "BEST_TEAM_AVERAGE", "WORST_TEAM_AVERAGE", "LATEST_WIN"],
    periods: ["LONGEST_WINNING_STREAK", "LONGEST_UNBEATEN_STREAK", "LONGEST_SCORELESS_STREAK", "LONGEST_CONCEDING_STREAK", "LONGEST_UNBEATEN_INTERVAL"]
  };
  const labels = {
    MOST_BAGRES: "Menor Desempenho", TOP_SCORER: "Artilharia histórica", ASSIST_LEADER: "Liderança em assistências",
    MOST_CRAQUES: "Mais vezes Craque", MOST_XERIFES: "Mais vezes Xerife", HIGHEST_PLAYER_AVERAGE: "Maior média elegível"
  };
  const typeLabel = item => labels[item.type] || item.title;
  const playerHref = player => player?.playerId ? `/players?playerId=${encodeURIComponent(player.playerId)}&from=club-history` : null;
  const matchHref = id => id ? `/history?matchId=${encodeURIComponent(id)}&from=club-history` : null;
  const ordered = (items, order) => order.flatMap(type => items.filter(item => item.type === type));

  function audit(item) {
    const a = item.audit;
    if (!a) return "";
    return `<details class="audit"><summary>Ver comprovação</summary><div class="audit-body"><strong>${esc(a.ruleId)} v${esc(a.ruleVersion)}</strong><br>${esc(a.criterion)}<br>Empates: ${esc(a.tiePolicy)}<br>Candidatos: ${esc(a.candidateCount)} · Elegíveis: ${esc(a.eligibleCandidateCount)}</div></details>`;
  }
  function section(kicker, title, copy, body) {
    if (!body) return "";
    return `<section class="memorial-section"><header class="section-head"><span class="section-kicker">${esc(kicker)}</span><h2>${esc(title)}</h2>${copy ? `<p class="section-copy">${esc(copy)}</p>` : ""}</header>${body}</section>`;
  }
  function continuation(item) {
    const player = item.involvedPlayers?.find(p => p.playerId);
    const match = item.involvedMatches?.[0];
    if (player) return `<a class="primary-link" href="${playerHref(player)}">Conhecer a trajetória</a>`;
    if (match) return `<a class="primary-link" href="${matchHref(match)}">Reviver esta partida</a>`;
    return "";
  }
  function lead(item) {
    if (!item) return `<article class="lead-story"><span class="section-kicker">Uma história em formação</span><h2>O primeiro capítulo ainda está sendo escrito.</h2><p>Novas partidas vão revelar as marcas que definem a trajetória do clube.</p></article>`;
    return `<article class="lead-story"><span class="section-kicker">História principal</span><h2>${esc(typeLabel(item))}</h2><div class="lead-value">${esc(item.value)}</div><p>${esc(item.description)}</p><div class="story-actions">${continuation(item)}</div>${audit(item)}</article>`;
  }
  function people(items, consumed) {
    const byPlayer = new Map();
    ordered(items, priorities.people).forEach(item => item.involvedPlayers.forEach(player => {
      const key = player.playerId || `unresolved:${player.name}`;
      if (!byPlayer.has(key)) byPlayer.set(key, { player, marks: [] });
      byPlayer.get(key).marks.push(item); consumed.add(item.type);
    }));
    const people = [...byPlayer.values()].slice(0, 4);
    if (!people.length) return "";
    return section("Protagonistas históricos", "Pessoas que deixaram marcas", "Reconhecimentos preservados ao longo do acervo.", `<div class="people-grid">${people.map(({player, marks}) => `<article class="person-mark"><span class="section-kicker">${marks.length > 1 ? "Marcas reunidas" : "Marca histórica"}</span><h3>${esc(player.name)}</h3><div class="mark-list">${marks.map(item => `<span class="mark">${esc(typeLabel(item))} · ${esc(item.value)}</span>`).join("")}</div>${playerHref(player) ? `<div class="entity-actions"><a class="secondary-link" href="${playerHref(player)}">Explorar trajetória</a></div>` : ""}${marks.map(audit).join("")}</article>`).join("")}</div>`);
  }
  function matchCard(summary, reason, id) {
    if (!summary) return `<article class="historical-item"><h3>${esc(typeLabel(reason))}</h3><p>${esc(reason.description)}</p>${audit(reason)}</article>`;
    return `<article><div class="historical-reason">${esc(typeLabel(reason))}</div><div class="match-summary-card"><div class="match-summary-card__head"><span class="result ${esc(summary.outcome.code)}">${esc(summary.outcome.label)}</span><div class="scoreline"><span class="team">${esc(summary.ourClub.name)}</span><strong class="score">${esc(summary.ourClub.score)} × ${esc(summary.opponentClub.score)}</strong><span class="team">${esc(summary.opponentClub.name)}</span></div><div class="match-meta">${esc(summary.dateLabel)}${summary.competition ? ` · ${esc(summary.competition)}` : ""}</div></div><a class="match-summary-card__action" href="${matchHref(id)}">Reviver a partida</a></div>${audit(reason)}</article>`;
  }
  function matches(items, summaries, consumed) {
    const chosen = [], ids = new Set();
    ordered(items, priorities.matches).forEach(item => item.involvedMatches.forEach(id => {
      if (chosen.length < 3 && !ids.has(id)) { chosen.push({id, item}); ids.add(id); consumed.add(item.type); }
    }));
    if (!chosen.length) return "";
    return section("Momentos preservados", "Partidas que ficaram na história", "Cada partida aparece pela conclusão histórica que ela sustenta.", `<div class="match-memorials">${chosen.map(({id,item}) => matchCard(summaries.get(id), item, id)).join("")}</div>`);
  }
  function historicalItems(items, className="marks-list") {
    return `<div class="${className}">${items.map(item => `<article class="historical-item"><h3>${esc(typeLabel(item))}</h3><p><strong>${esc(item.value)}</strong> — ${esc(item.description)}</p><div class="entity-actions">${continuation(item)}</div>${audit(item)}</article>`).join("")}</div>`;
  }
  function render(insightPayload, historyPayload) {
    const items = insightPayload.insights || [], count = insightPayload.sourceMatchCount || 0;
    const summaries = new Map((historyPayload?.matches || []).map(match => [match.matchId, match]));
    scope.textContent = count === 1 ? "1 partida preservada marca o início desta história." : `${count} partidas preservadas contam a trajetória da Associação BF.`;
    if (!count) { root.className = "memorial-state"; root.innerHTML = "<div><strong>A história começa na primeira partida.</strong><p>Quando um jogo for registrado, suas marcas passarão a viver aqui.</p></div>"; return; }
    const consumed = new Set();
    const main = ordered(items, priorities.lead)[0]; if (main) consumed.add(main.type);
    const peopleHtml = people(items, consumed);
    const matchesHtml = matches(items, summaries, consumed);
    const periods = ordered(items.filter(i => !consumed.has(i.type)), priorities.periods).slice(0,3); periods.forEach(i => consumed.add(i.type));
    const timeline = ["FIRST_WIN","LATEST_WIN"].map(t => items.find(i => i.type === t)).filter(Boolean).filter((item,index,array) => index === 0 || item.involvedMatches[0] !== array[0].involvedMatches[0]); timeline.forEach(i => consumed.add(i.type));
    const marks = items.filter(i => !consumed.has(i.type) && !priorities.periods.includes(i.type)).slice(0,5); marks.forEach(i => consumed.add(i.type));
    const rest = items.filter(i => !consumed.has(i.type));
    root.className = "";
    root.innerHTML = lead(main) + peopleHtml + matchesHtml
      + section("Marcas vigentes", "O que o acervo já revelou", "Conclusões que ajudam a compreender a trajetória.", marks.length ? historicalItems(marks) : "")
      + section("Períodos registrados", "Capítulos consecutivos", "Recortes do tempo preservados pelas partidas.", periods.length ? historicalItems(periods,"period-list") : "")
      + section("Linha do tempo", "Início e presente registrado", "Do primeiro resultado marcante ao capítulo mais recente.", timeline.length ? `<div class="timeline">${timeline.map((item,index) => `<article class="timeline-item"><span>${index ? "Presente registrado" : "Início registrado"}</span><h3>${esc(typeLabel(item))}</h3><p>${esc(item.description)}</p>${continuation(item)}${audit(item)}</article>`).join("")}</div>` : "")
      + section("Continuar explorando", "Outras descobertas", "Outras conclusões preservadas no acervo.", rest.length ? historicalItems(rest,"discoveries") : "");
  }
  async function load() {
    root.className = "memorial-state";
    try {
      const [insightsResult, historyResult] = await Promise.allSettled([fetch("/api/historical-insights"), fetch("/api/history/matches")]);
      if (insightsResult.status !== "fulfilled" || !insightsResult.value.ok) throw new Error("Não foi possível abrir a História do Clube agora.");
      const insights = await insightsResult.value.json();
      let history = null;
      if (historyResult.status === "fulfilled" && historyResult.value.ok) history = await historyResult.value.json();
      render(insights, history);
    } catch (error) {
      root.className = "memorial-state";
      root.innerHTML = `<div><strong>${esc(error.message)}</strong><p>Sua navegação pode continuar e você pode tentar novamente.</p><button class="retry" type="button">Tentar novamente</button></div>`;
      root.querySelector(".retry").addEventListener("click", load);
    }
  }
  load();
})();
