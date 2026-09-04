/**
 * Literal feedback phrases observed by the operator. This is a collector
 * shortcut only; it does not describe EA metrics or semantic meanings.
 */
export const DEFAULT_LIVE_FEEDBACK_PHRASES = [
  "Bela cavadinha",
  "Bela dividida",
  "Belo gol",
  "Boa finalização",
  "Boa jogada",
  "Boa tentativa",
  "Bom escanteio",
  "Bom passe",
  "Cuidado com o impedimento",
  "Continue tentando",
  "Lindo passe",
  "Melhore seu tempo de bola",
  "Não seja fominha",
  "Ótima assistência",
  "Ótima finta",
  "Ótima ideia",
  "Ótima interceptação",
  "Ótima jogada",
  "Ótimo empenho ofensivo",
  "Ótimo passe",
  "Peça o passe se estiver livre",
  "Perdeu a bola",
  "Por pouco continue assim",
  "Procure outra opção",
  "Procure opções melhores",
  "Procure voltar à posição natural",
  "Que azar",
  "Vá para a posição",
] as const;

export function createDefaultLiveFeedbackCounters(): Record<string, number> {
  return Object.fromEntries(DEFAULT_LIVE_FEEDBACK_PHRASES.map((phrase) => [phrase, 0]));
}

export type PhraseSuggestionKind = "NORMALIZED_EXACT" | "PREFIX" | "TYPO";

export type PhraseSuggestion = {
  phrase: string;
  kind: PhraseSuggestionKind;
  distance: number;
};

/** Comparison-only key. Callers must always persist the original phrase. */
export function normalizeLiveFeedbackPhrase(value: string): string {
  return value
    .trim()
    .toLocaleLowerCase("pt-BR")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\s+/g, " ");
}

export function uniqueExactPhrases(phrases: readonly string[]): string[] {
  return [...new Set(phrases)];
}

const liveFeedbackPhraseCollator = new Intl.Collator("pt-BR", {
  sensitivity: "base",
  numeric: true,
  usage: "sort",
});

/**
 * Presentation-only ordering for collector shortcuts. Exact phrase literals
 * remain untouched for drafts, review, Preview, and Import.
 */
export function sortLiveFeedbackPhrases(phrases: readonly string[]): string[] {
  return uniqueExactPhrases(phrases).sort((left, right) => {
    const primaryComparison = liveFeedbackPhraseCollator.compare(left, right);
    return primaryComparison || left.localeCompare(right, "pt-BR", { sensitivity: "variant", numeric: true });
  });
}

export function levenshteinDistance(left: string, right: string): number {
  if (left === right) return 0;
  if (left.length === 0) return right.length;
  if (right.length === 0) return left.length;

  let previous = Array.from({ length: right.length + 1 }, (_, index) => index);
  for (let leftIndex = 1; leftIndex <= left.length; leftIndex += 1) {
    const current = [leftIndex];
    for (let rightIndex = 1; rightIndex <= right.length; rightIndex += 1) {
      current[rightIndex] = Math.min(
        current[rightIndex - 1] + 1,
        previous[rightIndex] + 1,
        previous[rightIndex - 1] + (left[leftIndex - 1] === right[rightIndex - 1] ? 0 : 1),
      );
    }
    previous = current;
  }
  return previous[right.length];
}

function typoDistanceLimit(length: number): number {
  if (length < 6) return 0;
  return length > 14 ? 2 : 1;
}

/**
 * Conservative spelling-only assistance. Prefixes require at least four
 * characters and typos must be within a short edit distance.
 */
export function findLiveFeedbackSuggestions(
  input: string,
  candidates: readonly string[],
  maximum = 3,
): PhraseSuggestion[] {
  const normalizedInput = normalizeLiveFeedbackPhrase(input);
  if (!normalizedInput) return [];

  const byNormalizedPhrase = new Set<string>();
  const suggestions: PhraseSuggestion[] = [];

  for (const phrase of uniqueExactPhrases(candidates)) {
    const normalizedPhrase = normalizeLiveFeedbackPhrase(phrase);
    if (!normalizedPhrase || byNormalizedPhrase.has(normalizedPhrase)) continue;
    byNormalizedPhrase.add(normalizedPhrase);

    const distance = levenshteinDistance(normalizedInput, normalizedPhrase);
    if (normalizedInput === normalizedPhrase) {
      suggestions.push({ phrase, kind: "NORMALIZED_EXACT", distance });
      continue;
    }
    if (normalizedInput.length >= 4 && normalizedPhrase.startsWith(normalizedInput)) {
      suggestions.push({ phrase, kind: "PREFIX", distance });
      continue;
    }
    if (distance <= typoDistanceLimit(Math.max(normalizedInput.length, normalizedPhrase.length))) {
      suggestions.push({ phrase, kind: "TYPO", distance });
    }
  }

  const priority: Record<PhraseSuggestionKind, number> = {
    NORMALIZED_EXACT: 0,
    PREFIX: 1,
    TYPO: 2,
  };
  return suggestions
    .sort((left, right) => priority[left.kind] - priority[right.kind] || left.distance - right.distance)
    .slice(0, maximum);
}
