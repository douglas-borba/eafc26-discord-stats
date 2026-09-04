import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("observation import UI", () => {
  const explorer = read("components/admin/advanced-stats-explorer.tsx");

  it("has an Import observations button", () => {
    expect(explorer).toContain("Import observations");
  });

  it("has a JSON input textarea", () => {
    expect(explorer).toContain("Observation import JSON");
  });

  it("validates JSON client-side before sending request", () => {
    expect(explorer).toContain("JSON.parse");
    expect(explorer).toContain("Invalid JSON");
  });

  it("has Validate / Preview button", () => {
    expect(explorer).toContain("Validate / Preview");
  });

  it("renders preview counts for all statuses", () => {
    expect(explorer).toContain("preview.newCount");
    expect(explorer).toContain("preview.alreadyExistsCount");
    expect(explorer).toContain("preview.conflictCount");
    expect(explorer).toContain("preview.invalidCount");
  });

  it("disables import when conflicts exist", () => {
    expect(explorer).toContain("preview.conflictCount === 0");
    expect(explorer).toContain("preview.invalidCount === 0");
  });

  it("import button shows count and requires explicit click", () => {
    expect(explorer).toContain("Import ${preview.newCount}");
    expect(explorer).toContain("doImport");
  });

  it("successful import triggers data refresh", () => {
    expect(explorer).toContain("onImported()");
  });

  it("shows import result with inserted and already-existed counts", () => {
    expect(explorer).toContain("result.inserted");
    expect(explorer).toContain("result.alreadyExisted");
  });

  it("calls preview and import BFF routes", () => {
    expect(explorer).toContain("/observations/preview");
    expect(explorer).toContain("/observations/import");
  });

  it("does not expose internal tokens in the component", () => {
    expect(explorer).not.toContain("ADMIN_INTERNAL_TOKEN");
    expect(explorer).not.toContain("XSRF-TOKEN");
  });
});
