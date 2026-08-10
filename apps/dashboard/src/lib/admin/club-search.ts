import type { ClubSearchCandidate } from "./types";

const MAX_RESULTS = 20;

export function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^\w\s]/g, "")
    .trim()
    .replace(/\s+/g, " ");
}

export function rankCandidates(candidates: ClubSearchCandidate[], query: string): ClubSearchCandidate[] {
  const nq = normalize(query);
  if (!nq) return candidates.slice(0, MAX_RESULTS);

  return candidates
    .map((c) => ({ candidate: c, score: score(normalize(c.displayName), nq) }))
    .sort((a, b) => a.score - b.score)
    .slice(0, MAX_RESULTS)
    .map((e) => e.candidate);
}

function score(name: string, query: string): number {
  if (name === query) return 0;
  if (name.startsWith(query)) return 1;
  if (name.includes(query)) return 2;
  return 3 + levenshtein(name, query) / Math.max(name.length, query.length, 1);
}

function levenshtein(a: string, b: string): number {
  if (a.length === 0) return b.length;
  if (b.length === 0) return a.length;
  const matrix: number[][] = [];
  for (let i = 0; i <= a.length; i++) matrix[i] = [i];
  for (let j = 0; j <= b.length; j++) matrix[0][j] = j;
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      matrix[i][j] = Math.min(matrix[i - 1][j] + 1, matrix[i][j - 1] + 1, matrix[i - 1][j - 1] + cost);
    }
  }
  return matrix[a.length][b.length];
}

export function fallbackPrefix(query: string): string | null {
  const n = normalize(query);
  return n.length >= 5 ? n.slice(0, 5) : null;
}
