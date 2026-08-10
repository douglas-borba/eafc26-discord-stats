import { describe, expect, it } from "vitest";
import { normalize, rankCandidates, fallbackPrefix } from "@/lib/admin/club-search";
import type { ClubSearchCandidate } from "@/lib/admin/types";

function candidate(displayName: string, clubId = "1"): ClubSearchCandidate {
  return { displayName, clubId, platform: "common-gen5", currentDivision: null };
}

describe("normalize", () => {
  it("lowercases", () => {
    expect(normalize("BRASIL 2030")).toBe("brasil 2030");
  });

  it("removes accents", () => {
    expect(normalize("Gavilões")).toBe("gaviloes");
    expect(normalize("Associação")).toBe("associacao");
    expect(normalize("café")).toBe("cafe");
  });

  it("trims and collapses spaces", () => {
    expect(normalize("  BRASIL    2030  ")).toBe("brasil 2030");
  });

  it("removes simple punctuation", () => {
    expect(normalize("FC-Bayern.Munich!")).toBe("fcbayernmunich");
  });

  it("handles empty and whitespace", () => {
    expect(normalize("")).toBe("");
    expect(normalize("   ")).toBe("");
  });
});

describe("rankCandidates", () => {
  it("exact normalized match ranks first", () => {
    const results = rankCandidates([
      candidate("Gavilanes FC", "2"),
      candidate("Gavilões", "1"),
      candidate("GAVILOES X", "3"),
    ], "gaviloes");
    expect(results[0].clubId).toBe("1");
  });

  it("starts-with ranks above contains", () => {
    const results = rankCandidates([
      candidate("FC Brasil", "2"),
      candidate("Brasil 2030", "1"),
    ], "brasil");
    expect(results[0].clubId).toBe("1");
    expect(results[1].clubId).toBe("2");
  });

  it("contains ranks above fuzzy", () => {
    const results = rankCandidates([
      candidate("XYZABC", "2"),
      candidate("Team Brasil FC", "1"),
    ], "brasil");
    expect(results[0].clubId).toBe("1");
  });

  it("fuzzy ranks by edit distance", () => {
    const results = rankCandidates([
      candidate("Gavilanes FC", "1"),
      candidate("XYZABC Club", "2"),
    ], "gaviloes");
    expect(results[0].clubId).toBe("1");
  });

  it("handles accented query against unaccented names", () => {
    const results = rankCandidates([
      candidate("GAVILOES 13", "1"),
      candidate("Random Club", "2"),
    ], "Gavilões");
    expect(results[0].clubId).toBe("1");
  });

  it("handles unaccented query against accented names", () => {
    const results = rankCandidates([
      candidate("Associação BF", "1"),
      candidate("Random Club", "2"),
    ], "associacao bf");
    expect(results[0].clubId).toBe("1");
  });

  it("preserves official ClubId and displayName", () => {
    const results = rankCandidates([candidate("BRASIL    2030", "8874106")], "brasil");
    expect(results[0].clubId).toBe("8874106");
    expect(results[0].displayName).toBe("BRASIL    2030");
  });

  it("limits to 20 results", () => {
    const many = Array.from({ length: 30 }, (_, i) => candidate(`Club ${i}`, String(i)));
    expect(rankCandidates(many, "club").length).toBe(20);
  });

  it("returns empty for no candidates", () => {
    expect(rankCandidates([], "test")).toEqual([]);
  });
});

describe("fallbackPrefix", () => {
  it("returns first 5 chars for long queries", () => {
    expect(fallbackPrefix("gaviloes")).toBe("gavil");
  });

  it("normalizes before taking prefix", () => {
    expect(fallbackPrefix("Gavilões")).toBe("gavil");
  });

  it("returns null for short queries", () => {
    expect(fallbackPrefix("gavi")).toBeNull();
    expect(fallbackPrefix("abc")).toBeNull();
  });

  it("returns first 5 for exactly 5 chars", () => {
    expect(fallbackPrefix("gavil")).toBe("gavil");
  });

  it("returns null for empty/whitespace", () => {
    expect(fallbackPrefix("")).toBeNull();
    expect(fallbackPrefix("   ")).toBeNull();
  });
});
