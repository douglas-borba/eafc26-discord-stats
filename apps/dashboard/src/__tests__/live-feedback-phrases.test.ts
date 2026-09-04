import { describe, expect, it } from "vitest";
import {
  DEFAULT_LIVE_FEEDBACK_PHRASES,
  createDefaultLiveFeedbackCounters,
  findLiveFeedbackSuggestions,
  normalizeLiveFeedbackPhrase,
  sortLiveFeedbackPhrases,
  uniqueExactPhrases,
} from "@/lib/live-feedback-phrases";

describe("default live feedback phrase collection", () => {
  it("preserves the approved literal forms and excludes known incorrect variants", () => {
    expect(DEFAULT_LIVE_FEEDBACK_PHRASES).toEqual([
      "Bela cavadinha", "Bela dividida", "Belo gol", "Boa finalização", "Boa jogada", "Boa tentativa",
      "Bom escanteio", "Bom passe", "Cuidado com o impedimento", "Continue tentando", "Lindo passe",
      "Melhore seu tempo de bola", "Não seja fominha", "Ótima assistência", "Ótima finta", "Ótima ideia",
      "Ótima interceptação", "Ótima jogada", "Ótimo empenho ofensivo", "Ótimo passe",
      "Peça o passe se estiver livre", "Perdeu a bola", "Por pouco continue assim", "Procure outra opção",
      "Procure opções melhores", "Procure voltar à posição natural", "Que azar", "Vá para a posição",
    ]);
    expect(DEFAULT_LIVE_FEEDBACK_PHRASES).not.toContain("Ótima empenho ofensivo");
    expect(DEFAULT_LIVE_FEEDBACK_PHRASES).not.toContain("otima finta");
    expect(DEFAULT_LIVE_FEEDBACK_PHRASES).not.toContain("otima assistência");
    expect(DEFAULT_LIVE_FEEDBACK_PHRASES).not.toContain("otima empenho ofensivo");
  });

  it("starts every default shortcut at zero", () => {
    expect(createDefaultLiveFeedbackCounters()).toEqual(
      Object.fromEntries(DEFAULT_LIVE_FEEDBACK_PHRASES.map((phrase) => [phrase, 0])),
    );
  });

  it("presents all default shortcuts in deterministic Portuguese alphabetical order without changing literals", () => {
    const ordered = sortLiveFeedbackPhrases(DEFAULT_LIVE_FEEDBACK_PHRASES);

    expect(ordered).toEqual([...DEFAULT_LIVE_FEEDBACK_PHRASES].sort((left, right) =>
      left.localeCompare(right, "pt-BR", { sensitivity: "base", numeric: true })
      || left.localeCompare(right, "pt-BR", { sensitivity: "variant", numeric: true }),
    ));
    expect(ordered).toContain("Ótima assistência");
    expect(DEFAULT_LIVE_FEEDBACK_PHRASES).toEqual([
      "Bela cavadinha", "Bela dividida", "Belo gol", "Boa finalização", "Boa jogada", "Boa tentativa",
      "Bom escanteio", "Bom passe", "Cuidado com o impedimento", "Continue tentando", "Lindo passe",
      "Melhore seu tempo de bola", "Não seja fominha", "Ótima assistência", "Ótima finta", "Ótima ideia",
      "Ótima interceptação", "Ótima jogada", "Ótimo empenho ofensivo", "Ótimo passe",
      "Peça o passe se estiver livre", "Perdeu a bola", "Por pouco continue assim", "Procure outra opção",
      "Procure opções melhores", "Procure voltar à posição natural", "Que azar", "Vá para a posição",
    ]);
  });

  it("sorts a combined historical and manual palette as one collection without changing exact literals", () => {
    const phrases = ["Ótima assistência", "Bela cavadinha", "Bela batida", "ótima assistência", "Bela cavadinha"];
    const originalPhrases = [...phrases];

    expect(sortLiveFeedbackPhrases(phrases)).toEqual([
      "Bela batida",
      "Bela cavadinha",
      "ótima assistência",
      "Ótima assistência",
    ]);
    expect(uniqueExactPhrases(phrases)).toEqual([
      "Ótima assistência",
      "Bela cavadinha",
      "Bela batida",
      "ótima assistência",
    ]);
    expect(phrases).toEqual(originalPhrases);
  });

  it("keeps the same alphabetical positions regardless of counter values or filtering", () => {
    const phrases = ["Ótima jogada", "Bela dividida", "Bela cavadinha", "Boa jogada"];
    const fullOrder = sortLiveFeedbackPhrases(phrases);
    const countsBefore = { "Ótima jogada": 0, "Bela dividida": 0, "Bela cavadinha": 0, "Boa jogada": 0 };
    const countsAfter = { ...countsBefore, "Bela dividida": 3, "Ótima jogada": 1 };
    const filtered = fullOrder.filter((phrase) => normalizeLiveFeedbackPhrase(phrase).includes("jogada"));
    const afterClearingFilter = fullOrder;

    expect(sortLiveFeedbackPhrases(Object.keys(countsBefore))).toEqual(fullOrder);
    expect(sortLiveFeedbackPhrases(Object.keys(countsAfter))).toEqual(fullOrder);
    expect(filtered).toEqual(["Boa jogada", "Ótima jogada"]);
    expect(afterClearingFilter).toEqual(fullOrder);
    expect(fullOrder).toEqual(["Bela cavadinha", "Bela dividida", "Boa jogada", "Ótima jogada"]);
  });
});

describe("live feedback phrase comparison", () => {
  it("uses accent-, case-, and whitespace-insensitive comparison keys only", () => {
    expect(normalizeLiveFeedbackPhrase("Ótima finta")).toBe("otima finta");
    expect(normalizeLiveFeedbackPhrase("  OTIMA   FINTA ")).toBe("otima finta");
    expect(normalizeLiveFeedbackPhrase("Não seja fominha")).toBe("nao seja fominha");
  });

  it("finds normalized exact matches without changing the returned exact phrase", () => {
    expect(findLiveFeedbackSuggestions("otima assistencia", DEFAULT_LIVE_FEEDBACK_PHRASES)).toEqual([
      { phrase: "Ótima assistência", kind: "NORMALIZED_EXACT", distance: 0 },
    ]);
  });

  it("finds small textual typos but keeps semantically distinct phrases apart", () => {
    expect(findLiveFeedbackSuggestions("otima fint", DEFAULT_LIVE_FEEDBACK_PHRASES)[0]).toMatchObject({
      phrase: "Ótima finta",
    });
    expect(findLiveFeedbackSuggestions("nao seja fomina", DEFAULT_LIVE_FEEDBACK_PHRASES)[0]).toMatchObject({
      phrase: "Não seja fominha",
      kind: "TYPO",
    });
    expect(findLiveFeedbackSuggestions("Bom passe", ["Ótimo passe"])).toEqual([]);
    expect(findLiveFeedbackSuggestions("Boa jogada", ["Ótima jogada"])).toEqual([]);
  });

  it("suggests the known literal phrase for the persisted empenho spelling variant", () => {
    expect(findLiveFeedbackSuggestions("otimo emepenho ofensivo", DEFAULT_LIVE_FEEDBACK_PHRASES)[0]).toMatchObject({
      phrase: "Ótimo empenho ofensivo",
      kind: "TYPO",
    });
  });

  it("returns no more than three strong candidates", () => {
    expect(findLiveFeedbackSuggestions("otima", DEFAULT_LIVE_FEEDBACK_PHRASES)).toHaveLength(3);
    expect(findLiveFeedbackSuggestions("texto sem relação", DEFAULT_LIVE_FEEDBACK_PHRASES)).toEqual([]);
  });
});
