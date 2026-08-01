(() => {
  const characters = {
    CRAQUE: {
      label: "Craque",
      icon: "⭐",
      tone: "highlight",
      message: "Uma atuação que fez a diferença nesta partida.",
    },
    BAGRE: {
      label: "Menor Desempenho",
      icon: "📉",
      tone: "development",
      fact: "Menor desempenho entre os jogadores elegíveis nesta partida.",
      message: "Nem toda partida sai como esperado. A próxima é uma nova oportunidade para responder em campo.",
    },
    XERIFE: {
      label: "Xerife",
      icon: "🛡️",
      tone: "defense",
      message: "Consistência e segurança para proteger o time.",
    },
  };

  const stories = {
    DECISIVE: {
      title: "Fez a Diferença",
      icon: "⚡",
      tone: "impact",
      fact: "A engine identificou uma participação ofensiva decisiva.",
      message: "Uma atuação que mudou o rumo da partida, sustentada pelos fatos do jogo.",
    },
    CONSTANT_THREAT: {
      title: "Perigo Constante",
      icon: "🎯",
      tone: "pressure",
      fact: "A engine identificou presença ofensiva constante.",
      message: "Participação ofensiva frequente, pressionando e criando oportunidades.",
    },
    NEAR_MISS: {
      title: "Ficou no Quase",
      icon: "⏳",
      tone: "near-miss",
      message: "A presença ofensiva apareceu; faltou transformar mais oportunidades em resultado.",
    },
    RED_CARD: {
      title: "Cartão Vermelho",
      icon: "🟥",
      tone: "discipline",
      fact: "A partida registrou uma ocorrência disciplinar.",
      message: "Um momento difícil que também faz parte da história deste jogo.",
    },
    PASS_PRECISION: {
      title: "Passe de Precisão",
      icon: "🎯",
      tone: "precision",
      fact: "A engine reconheceu uma atuação de destaque na precisão dos passes.",
      message: "Consistência com a bola para dar continuidade ao jogo do time.",
    },
    LOST_MAIL: {
      title: "Correio Extraviado",
      icon: "📨",
      tone: "development",
      fact: "A precisão de passes ficou abaixo da referência coletiva registrada.",
      message: "Um aspecto desta atuação que pode encontrar uma resposta diferente no próximo jogo.",
    },
    GOALKEEPER: {
      title: "Muralha",
      icon: "🧤",
      tone: "defense",
      fact: "A atuação no gol recebeu uma leitura específica da engine.",
      message: "A presença do goleiro também escreveu parte desta partida.",
    },
    DEFAULT: {
      icon: "•",
      tone: "neutral",
      fact: "Uma história identificada deterministicamente nesta partida.",
      message: "Mais um fato que ajuda a compreender como o jogo aconteceu.",
    },
  };

  window.EafcEditorialDesign = Object.freeze({
    characters: Object.freeze(characters),
    stories: Object.freeze(stories),
  });
})();
