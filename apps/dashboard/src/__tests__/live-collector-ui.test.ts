import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("live collector UI", () => {
  const explorer = read("components/admin/advanced-stats-explorer.tsx");
  const styles = read("app/globals.css");
  const draftLifecycle = read("lib/live-collector-draft.ts");

  it("defines LiveCollector component", () => {
    expect(explorer).toContain("function LiveCollector");
  });

  it("uses localStorage for draft persistence", () => {
    expect(explorer).toContain("fc-stats-live-collector-draft");
    expect(explorer).toContain("localStorage.getItem");
    expect(explorer).toContain("localStorage.setItem");
    expect(explorer).toContain("localStorage.removeItem");
  });

  it("wraps localStorage access in try/catch", () => {
    expect(explorer).toMatch(/try\s*\{[^}]*localStorage/);
  });

  it("has increment and decrement functions", () => {
    expect(explorer).toContain("increment(phrase)");
    expect(explorer).toContain("decrement(phrase)");
  });

  it("uses responsive tap targets without an internal phrase scrollbar", () => {
    expect(styles).toContain(".live-collector-phrase-grid");
    expect(styles).toContain("grid-template-columns: minmax(0, 1fr)");
    expect(styles).toContain("@media (min-width: 720px)");
    expect(styles).toContain("repeat(2, minmax(0, 1fr))");
    expect(styles).toContain("@media (min-width: 1024px)");
    expect(styles).toContain("repeat(3, minmax(0, 1fr))");
    expect(styles).toContain("width: 52px");
    expect(styles).toContain("height: 52px");
    expect(styles).toContain("width: 40px");
    expect(styles).toContain("height: 40px");
    const collectPhase = explorer.slice(explorer.indexOf("// COLLECT phase"));
    expect(collectPhase).not.toContain('maxHeight: "60vh"');
    expect(collectPhase).not.toContain("overflowY: \"auto\"");
  });

  it("has add new phrase input", () => {
    expect(explorer).toContain("Nova frase EA");
    expect(explorer).toContain("+ Frase");
  });

  it("has collect, review, associate, and save phases", () => {
    expect(explorer).toContain('"collect"');
    expect(explorer).toContain('"review"');
    expect(explorer).toContain('"associate"');
    expect(explorer).toContain('"save"');
  });

  it("supports AT_LEAST and EXACT completeness", () => {
    expect(explorer).toContain("AT_LEAST");
    expect(explorer).toContain("EXACT");
    expect(explorer).toContain("Completude");
  });

  it("uses existing preview/import endpoints for safe save", () => {
    expect(explorer).toContain("observations/preview");
    expect(explorer).toContain("observations/import");
  });

  it("keeps the opponent reminder out of preview and import payloads", () => {
    const collectorCode = explorer.slice(
      explorer.indexOf("function LiveCollector"),
      explorer.indexOf("export function ObservationComparisonView"),
    );
    const previewPayload = collectorCode.slice(collectorCode.indexOf("const doPreview"), collectorCode.indexOf("const doImport"));
    const importPayload = collectorCode.slice(collectorCode.indexOf("const doImport"), collectorCode.indexOf("if (!open)"));
    expect(previewPayload).not.toContain("opponentName");
    expect(importPayload).not.toContain("opponentName");
    expect(previewPayload).not.toContain("normalizeLiveFeedbackPhrase");
    expect(importPayload).not.toContain("normalizeLiveFeedbackPhrase");
  });

  it("clears draft only on server success", () => {
    const importSection = explorer.slice(explorer.indexOf("doImport"));
    expect(importSection).toContain("clearDraft()");
  });

  it("has discard confirmation", () => {
    expect(explorer).toContain("confirmDiscard");
    expect(explorer).toContain("Confirmar descarte");
  });

  it("does not expose aggregate codes or semantic information in the collector", () => {
    const collectorCode = explorer.slice(
      explorer.indexOf("function LiveCollector"),
      explorer.indexOf("export function ObservationComparisonView"),
    );
    expect(collectorCode).not.toContain("agg0[");
    expect(collectorCode).not.toContain("HIGH_PRIORITY");
    expect(collectorCode).not.toContain("candidate meaning");
  });

  it("has filter input for large phrase lists", () => {
    expect(explorer).toContain("Filtrar frases");
  });

  it("sorts the combined visible collection alphabetically while keeping count changes out of ordering", () => {
    const orderSection = explorer.slice(explorer.indexOf("const orderedPhrases"), explorer.indexOf("// Association: load recent matches"));
    expect(orderSection).toContain("sortLiveFeedbackPhrases(phraseOrder.filter");
    expect(orderSection).toContain("? orderedPhrases.filter");
    expect(orderSection).toContain(": orderedPhrases");
    expect(explorer).toContain("Math.max(0, current - 1)");
    expect(explorer).toContain("phrases: { ...d.phrases, [trimmed]: 0 }");
  });

  it("keeps normal row-major grid placement for alphabetical DOM order", () => {
    const gridSection = styles.slice(styles.indexOf(".live-collector-phrase-grid"), styles.indexOf(".live-collector-phrase-item"));
    expect(gridSection).toContain("display: grid");
    expect(gridSection).not.toContain("grid-auto-flow: column");
  });

  it("stores an optional opponent reminder and restores older drafts", () => {
    expect(explorer).toContain("opponentName: string");
    expect(explorer).toContain('opponentName: typeof parsed.opponentName === "string" ? parsed.opponentName.trim() : ""');
    expect(explorer).toContain("localStorage.getItem(DRAFT_STORAGE_KEY)");
    expect(explorer).toContain("localStorage.setItem(DRAFT_STORAGE_KEY");
    expect(draftLifecycle).toContain('opponentName: ""');
    expect(explorer).toContain("Adversário (lembrete local)");
    expect(explorer).toContain("Adversário da partida");
  });

  it("starts every new collection without a canonical match or player association", () => {
    expect(explorer).toContain("createLiveCollectorDraft(clubId)");
    expect(explorer).not.toContain("matchId: matchId ?? null");
    expect(explorer).not.toContain("playerId: playerId ?? null");
    expect(draftLifecycle).toContain("matchId: null");
    expect(draftLifecycle).toContain("playerId: null");
    expect(draftLifecycle).toContain("associationDraftStartedAt: null");
  });

  it("shows the opponent reminder throughout collect, review, and association", () => {
    expect(explorer).toContain("COLETANDO AO VIVO");
    expect(explorer).toContain("vs. {draft.opponentName}");
    expect(explorer).toContain("Coleta: vs. {draft.opponentName}");
  });

  it("shows session summary counters", () => {
    expect(explorer).toContain("totalCount");
    expect(explorer).toContain("activeCount");
  });

  it("loads phrase palette from observation-phrases endpoint", () => {
    expect(explorer).toContain("observation-phrases");
    expect(explorer).toContain("setHistoricalPalette");
    expect(explorer).toContain("uniqueExactPhrases([...currentOrder, ...nextPalette])");
  });

  it("initializes only new drafts with the frontend-owned default phrase collection", () => {
    expect(draftLifecycle).toContain("createDefaultLiveFeedbackCounters()");
    expect(draftLifecycle).toContain("phraseCollectionVersion: 1");
    expect(explorer).toContain("current.phraseCollectionVersion !== 1");
  });

  it("combines default, historical, and manual phrases without exact duplicates", () => {
    expect(explorer).toContain("uniqueExactPhrases([...DEFAULT_LIVE_FEEDBACK_PHRASES, ...historicalPalette])");
    expect(explorer).toContain("uniqueExactPhrases([...knownPhrases, ...Object.keys(draft?.phrases ?? {})])");
    expect(explorer).toContain("uniqueExactPhrases([...currentOrder, trimmed])");
  });

  it("keeps zero-count shortcuts out of Preview and Import", () => {
    const collectorCode = explorer.slice(
      explorer.indexOf("function LiveCollector"),
      explorer.indexOf("export function ObservationComparisonView"),
    );
    const previewPayload = collectorCode.slice(collectorCode.indexOf("const doPreview"), collectorCode.indexOf("const doImport"));
    const importPayload = collectorCode.slice(collectorCode.indexOf("const doImport"), collectorCode.indexOf("if (!open)"));
    expect(previewPayload).toContain("buildLiveCollectorObservationInputs(draft)");
    expect(importPayload).toContain("buildLiveCollectorObservationInputs(draft)");
    expect(draftLifecycle).toContain(".filter(([, count]) => count > 0)");
  });

  it("keeps spelling assistance local, explicit, and bounded", () => {
    expect(explorer).toContain("findLiveFeedbackSuggestions");
    expect(explorer).toContain("Você quis dizer?");
    expect(explorer).toContain("Manter como nova");
    expect(explorer).toContain("Possível frase já conhecida");
    expect(explorer).toContain("mergeIntoKnownPhrase");
    const collectorCode = explorer.slice(
      explorer.indexOf("function LiveCollector"),
      explorer.indexOf("export function ObservationComparisonView"),
    );
    expect(collectorCode).not.toContain("fetch(`/ea/");
  });

  it("has match association flow", () => {
    expect(explorer).toContain("Associar partida");
    expect(explorer).toContain("selectMatch");
    expect(explorer).toContain("selectPlayer");
    expect(explorer).toContain("Trocar associação");
  });

  it("guards Preview and Import with the current draft association invariant", () => {
    const collectorCode = explorer.slice(
      explorer.indexOf("function LiveCollector"),
      explorer.indexOf("export function ObservationComparisonView"),
    );
    const previewCode = collectorCode.slice(collectorCode.indexOf("const doPreview"), collectorCode.indexOf("const doImport"));
    const importCode = collectorCode.slice(collectorCode.indexOf("const doImport"), collectorCode.indexOf("if (!open)"));
    expect(previewCode).toContain("hasCurrentLiveCollectorAssociation(draft)");
    expect(importCode).toContain("hasCurrentLiveCollectorAssociation(draft)");
    expect(previewCode).toContain("beginAssociation");
    expect(importCode).toContain("beginAssociation");
  });

  it("keeps association manual and does not call EA from the collector", () => {
    const collectorCode = explorer.slice(
      explorer.indexOf("function LiveCollector"),
      explorer.indexOf("export function ObservationComparisonView"),
    );
    const associationCode = collectorCode.slice(collectorCode.indexOf("const loadMatches"), collectorCode.indexOf("// Save flow"));
    expect(collectorCode).not.toContain("/ea/");
    expect(collectorCode).toContain("onClick={() => selectMatch(m.matchId)}");
    expect(associationCode).not.toContain("opponentName");
  });

  it("renders success state after save", () => {
    expect(explorer).toContain("Coleta salva com sucesso");
  });
});

describe("live collector BFF route", () => {
  const route = read("app/api/admin/explorer/clubs/[clubId]/players/[playerId]/observation-phrases/route.ts");

  it("uses proxyAdminRequest", () => {
    expect(route).toContain("proxyAdminRequest");
  });

  it("is a GET endpoint", () => {
    expect(route).toContain("export async function GET");
  });

  it("encodes path parameters", () => {
    expect(route).toContain("encodeURIComponent(clubId)");
    expect(route).toContain("encodeURIComponent(playerId)");
  });
});
