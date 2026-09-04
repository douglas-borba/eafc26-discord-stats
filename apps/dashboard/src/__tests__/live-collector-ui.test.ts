import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("live collector UI", () => {
  const explorer = read("components/admin/advanced-stats-explorer.tsx");

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

  it("renders mobile-friendly large tap targets", () => {
    expect(explorer).toContain("width: 54");
    expect(explorer).toContain("height: 54");
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

  it("shows session summary counters", () => {
    expect(explorer).toContain("totalCount");
    expect(explorer).toContain("activeCount");
  });

  it("loads phrase palette from observation-phrases endpoint", () => {
    expect(explorer).toContain("observation-phrases");
    expect(explorer).toContain("setPalette");
  });

  it("has match association flow", () => {
    expect(explorer).toContain("Associar partida");
    expect(explorer).toContain("selectMatch");
    expect(explorer).toContain("selectPlayer");
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
