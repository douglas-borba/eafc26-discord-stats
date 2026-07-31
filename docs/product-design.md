# Product Design do EA FC STATS

Este documento é a referência oficial para decisões futuras de UX e UI.

## Visão do produto

O EA FC STATS não é um dashboard de estatísticas. É um sistema que transforma
dados de partidas em histórias sobre a evolução do clube e de seus jogadores.

A experiência é organizada em torno de três entidades conectadas:

- Partidas;
- Jogadores;
- História do Clube.

A Visão Geral funciona como porta de entrada editorial para essas entidades.
Configurações permanece como área utilitária, separada da exploração esportiva.

### Por que “História do Clube”

A antiga área de Insights reúne recordes, marcos, sequências, líderes e fatos
relevantes. “Estatísticas” descreve a matéria-prima, não seu propósito.
“Recordes” seria restritivo, “Destaques” seria genérico e “Hall da Fama”
privilegiaria jogadores. “História do Clube” representa o conjunto e reforça a
identidade narrativa do produto.

## Princípios do Produto

### A narrativa vem antes da estatística

Primeiro mostramos o que aconteceu, quem protagonizou e por que foi relevante.
Os números aparecem como sustentação da história.

### Toda página responde uma pergunta do usuário

Cada página deve ter um propósito reconhecível no vocabulário do futebol, não
no vocabulário da implementação.

### Toda informação importante permite exploração

Partidas, jogadores, premiações, recordes e marcos devem conduzir naturalmente
a seus contextos relacionados.

### O usuário não procura dados; ele descobre histórias

A interface deve revelar relações relevantes e incentivar continuidade, sem
exigir que o usuário conheça previamente o módulo técnico que contém a resposta.

### Evidências técnicas aparecem somente sob demanda

RuleReference, DecisionEvidence, versões, identificadores e critérios continuam
acessíveis para auditoria, mas não competem com a narrativa principal.

### Partidas, jogadores e a História do Clube são entidades conectadas

Essas entidades não são módulos isolados. Cada uma deve servir como entrada
natural para as demais.

### A interface não cria interpretações

A apresentação nunca decide regras de futebol, vencedores, premiações ou
narrativas. Ela apresenta exclusivamente decisões produzidas pela engine.

### Consistência é mais importante do que variedade visual

O mesmo conceito deve preservar nome, componente, cor, comportamento e
hierarquia em toda a aplicação.

### Toda narrativa segue Fato → Evidência → Mensagem

O fato é a conclusão objetiva produzida pela engine. A evidência apresenta o
motivo e os dados determinísticos existentes. A mensagem acrescenta um
complemento humano, curto e coerente com o espírito esportivo.

Nenhuma mensagem pode alterar critérios, inventar justificativas, exagerar uma
conclusão ou contradizer a engine.

## Tom Editorial

O produto celebra boas atuações sem exagerar seu impacto. Desempenhos abaixo
dos demais são reconhecidos com honestidade e respeito, sempre limitados à
partida observada.

O desempenho de uma partida não define a capacidade de um jogador. A interface
deve falar sobre “esta atuação” e “esta partida”, nunca classificar a pessoa.
Nenhuma mensagem pode ridicularizar, humilhar, atribuir falta de esforço ou
culpar individualmente um jogador por um resultado coletivo.

A comunicação incentiva evolução contínua sem esconder os fatos. Evidência e
mensagem possuem responsabilidades diferentes:

```text
Fato
→ o que a engine concluiu

Evidência
→ por que concluiu

Mensagem
→ como comunicar humanamente
```

A mensagem permanece curta, esportiva e conectada ao jogo. O produto evita
frases genéricas de autoajuda e inferências sobre intenção, potencial ou estado
emocional que não tenham sido produzidas pela engine.

### Voz das premiações e narrativas

| Decisão canônica | Apresentação | Propósito editorial |
|---|---|---|
| Craque | Craque | Celebrar protagonismo sem exagero |
| Xerife | Xerife | Valorizar consistência e segurança defensiva |
| BAGRE | Menor Desempenho | Registrar a menor atuação elegível com respeito e perspectiva de evolução |
| CONSTANT_THREAT | Perigo Constante | Reconhecer presença ofensiva contínua e criação de oportunidades |
| COULD_HAVE_DECIDED / FELL_SHORT / LACKED_COMPOSURE | Ficou no Quase | Valorizar participação ofensiva relevante sem tratar conversão insuficiente como fracasso pessoal |
| DECISIVE | Fez a Diferença | Destacar impacto comprovadamente decisivo |
| RED_CARD | Cartão Vermelho | Registrar o fato disciplinar sem ridicularização ou julgamento de caráter |
| PASS_PRECISION | Passe de Precisão | Celebrar segurança e consistência na circulação da bola |
| LOST_MAIL | Correio Extraviado | Comunicar precisão abaixo da referência coletiva com objetividade e respeito |
| GOALKEEPER | Muralha / atuação no gol | Contextualizar a participação do goleiro conforme a leitura canônica |
| EA_RECOGNIZED_MVP | MVP da EA | Registrar o reconhecimento externo sem confundi-lo com a decisão de Craque |
| GOALS / ASSISTS | Participações ofensivas | Reconhecer contribuições factuais sem criar uma nova premiação |
| HIGHLIGHTS | Destaques por nota | Dar contexto às avaliações sem produzir outro ranking |

`BAGRE` permanece como identificador técnico. “Menor Desempenho” é o rótulo
oficial apresentado ao usuário. A interface descreve uma atuação, nunca uma
identidade.

### Mensagens editoriais iniciais

- Craque: “Uma atuação que fez a diferença nesta partida.”
- Xerife: “Consistência e segurança para proteger o time.”
- Menor Desempenho: “Nem toda partida sai como esperado. A próxima é uma nova oportunidade para responder em campo.”
- Perigo Constante: “Participação ofensiva constante, pressionando e criando oportunidades.”
- Ficou no Quase: “A presença ofensiva apareceu; faltou transformar mais oportunidades em resultado.”
- Fez a Diferença: “Uma atuação que mudou o rumo da partida, sustentada pelos fatos do jogo.”
- Cartão Vermelho: “Um momento difícil que também faz parte da história deste jogo.”
- Passe de Precisão: “Consistência com a bola para dar continuidade ao jogo do time.”
- Correio Extraviado: “Um aspecto desta atuação que pode encontrar uma resposta diferente no próximo jogo.”
- Muralha: “A presença do goleiro também escreveu parte desta partida.”
- MVP da EA: “Um reconhecimento registrado pela própria EA nesta atuação.”
- Participações ofensivas: “Gols e assistências que ajudam a contar como o placar foi construído.”

Essas mensagens pertencem exclusivamente à apresentação e nunca substituem o
motivo ou as evidências determinísticas.

## Design System Editorial

O Design System Editorial é a linguagem visual oficial do EA FC STATS. Seu
objetivo não é produzir variedade decorativa: é permitir que o propósito de
cada informação seja reconhecido antes da leitura. Todos os consumidores web
devem usar os tokens, componentes e estados descritos nesta seção.

### Fundamentos visuais

- **Superfícies:** fundo profundo, superfície estrutural e superfície elevada.
  Elevação comunica hierarquia editorial, nunca importância esportiva.
- **Texto:** branco para fatos centrais, cinza claro para mensagens e cinza
  médio para contexto. Chaves técnicas usam fonte monoespaçada.
- **Cor:** possui significado estável. Verde, amarelo e vermelho ficam
  reservados a resultado; os acentos de personagens e capítulos identificam
  categorias, não notas de qualidade.
- **Forma:** raios discretos e bordas finas preservam a sensação de arquivo
  esportivo. Sombras não são necessárias para estabelecer hierarquia.
- **Foco:** todo controle interativo recebe contorno visível na cor de ação.
  Cor nunca é o único meio de comunicar um estado.

### Hierarquia e ordem de leitura

1. `HeroSection`: resultado, placar, clubes, competição e data.
2. `CharacterCard`: quem marcou a partida.
3. `StoryChapter`: como o jogo aconteceu.
4. `MetricStrip`: números que sustentam a leitura.
5. `PlayerPerformanceTable` ou `PlayerPerformanceCard`: detalhe individual.
6. `EvidenceDisclosure`: critérios técnicos sob demanda.

A manchete usa o maior tipo e o maior respiro da página. Capítulos recebem mais
ritmo e espaço que métricas. Dados coletivos usam uma faixa aberta, sem criar um
painel tradicional. Evidências permanecem recolhidas por padrão.

### Espaçamento e legibilidade

A escala oficial é `4, 8, 12, 16, 24, 32 e 48px`. Distâncias internas usam até
`24px`; mudanças de assunto usam `32` ou `48px`. Textos narrativos possuem
largura máxima de `76ch`. Blocos adjacentes são separados por respiro e um
divisor progressivo, nunca por uma sucessão indiscriminada de cards.

No desktop, a leitura acontece da esquerda para a direita apenas dentro de um
mesmo grupo semântico. No mobile, todos os grupos se tornam uma sequência
vertical. Tabelas técnicas são substituídas por cards de desempenho, mantendo
nome, posição e nota como primeira linha de leitura.

### Guia visual das seções

| Seção | Linguagem visual | Prioridade |
|---|---|---|
| A Partida | Abertura espaçosa de reportagem, placar dominante e resultado em badge | Resultado → placar → clubes → contexto |
| Personagens da Partida | Mesma anatomia, acento e ícone próprios por reconhecimento | Categoria → jogador → fato → evidência → mensagem |
| A História do Jogo | Capítulos com trilho cromático, título editorial e mensagem separada da prova | Fato → envolvidos → evidência → mensagem |
| O Time em Números | Faixa aberta, valores grandes e divisores leves | Valor → rótulo; nunca compete com capítulos |
| Jogador por Jogador | Grade técnica de alta legibilidade; nome e nota dominantes | Jogador → nota → posição → indicadores |
| Critérios e Evidências | Superfície documental, chaves técnicas e disclosures fechados | Título técnico → regra → evidência → proveniência |

### Sistema oficial de iconografia

| Ícone | Uso permanente | Regra |
|---|---|---|
| ⭐ | Craque e protagonismo reconhecido | Não usar como decoração genérica |
| 🛡️ | Xerife e consistência defensiva | Reservado a leituras defensivas canônicas |
| 📉 | Menor Desempenho | Refere-se à atuação na partida, nunca à pessoa |
| ⚡ | Fez a Diferença | Exige impacto decisivo já concluído pela engine |
| 🎯 | Perigo Constante e precisão | O título sempre esclarece o contexto |
| ⏳ | Ficou no Quase | Reconhece participação sem comunicar fracasso |
| 🟥 | Cartão Vermelho | Registro disciplinar objetivo |
| 📨 | Correio Extraviado | Leitura canônica de precisão abaixo da referência |
| 🧤 | Muralha | Participação do goleiro reconhecida pela engine |

Ícones acompanham sempre um rótulo textual, possuem função de reconhecimento e
são ignorados por leitores de tela quando repetem esse rótulo. Novas categorias
devem reutilizar um ícone apenas quando o significado permanecer inequívoco.

### Componentes oficiais

| Componente | Propósito e responsabilidade | Estados e comportamento | Reutilização prevista |
|---|---|---|---|
| `EditorialSection` | Delimitar um assunto e sua pergunta editorial | Título, kicker, pergunta opcional e divisor entre assuntos | Todas as páginas narrativas |
| `HeroSection` | Abrir uma entidade como manchete | Resultado, neutro, carregando e indisponível; conteúdo responsivo | Partida e, com variante de conteúdo, perfil de jogador e recorde |
| `CharacterCard` | Apresentar reconhecimento individual sem variar sua anatomia | Concedido e não concedido; tons `highlight`, `defense` e `development` | Premiações de Partidas e reconhecimentos em Jogadores |
| `StoryChapter` | Contar uma decisão como capítulo em Fato → Evidência → Mensagem | Tons `impact`, `pressure`, `near-miss`, `precision`, `defense`, `discipline`, `development` e `neutral` | Partidas, trajetória do jogador e História do Clube |
| `MetricStrip` | Sustentar uma narrativa com poucas métricas | 1–3 colunas; quebra para duas e depois uma no mobile | Todas as entidades, sem criar dashboards densos |
| `EvidenceDisclosure` | Expor auditoria no segundo nível de profundidade | Fechado por padrão, aberto por ação explícita e foco visível | Toda decisão auditável |
| `PlayerPerformanceTable` | Comparar atuações individuais no desktop | Cabeçalho fixo semanticamente, rolagem horizontal de segurança | Partidas e listas técnicas futuras |
| `PlayerPerformanceCard` | Preservar a hierarquia da tabela no mobile | Nome e nota dominantes; posição e indicadores secundários | Partidas e resumos de perfil |
| `OutcomeBadge` | Identificar vitória, empate ou derrota | `WIN`, `DRAW`, `LOSS`; texto, ícone e cor combinados | Listas, heróis e comparações |
| `SectionDivider` | Marcar mudança de assunto sem criar outro contêiner | Linha progressiva aplicada entre `EditorialSection` | Páginas editoriais longas |
| `EmptyEditorialState` | Explicar ausência de conteúdo sem inventar narrativa | Página ou compacto; instrução contextual quando houver | Arquivos, histórias e perfis |
| `LoadingEditorialState` | Comunicar espera sem antecipar conteúdo | Página ou região; nunca substitui erro | Consumidores assíncronos |

### Sistema dos personagens

Todos os personagens compartilham categoria, ícone, nome, fato, evidência e
mensagem. O sistema permite individualidade apenas pelo ícone e pelo acento:

- Craque: estrela e acento dourado, com maior energia de reconhecimento;
- Xerife: escudo e acento azul, associado a segurança e consistência;
- Menor Desempenho: gráfico descendente e acento coral sóbrio, sem vermelho de
  erro ou linguagem punitiva;
- não concedido: borda tracejada, superfície neutra e mensagem humana omitida.

Fez a Diferença, Perigo Constante e Ficou no Quase são capítulos narrativos,
não novas premiações visuais. Usam respectivamente raio dourado, alvo alaranjado
e ampulheta violeta dentro da anatomia compartilhada de `StoryChapter`.

### Reuso na Etapa 3

Podem ser reutilizados sem alteração em Jogadores: `EditorialSection`,
`StoryChapter`, `MetricStrip`, `EvidenceDisclosure`, `OutcomeBadge`, estados e
divisores. `CharacterCard` também pode representar reconhecimentos já
persistidos, desde que sua semântica continue sendo uma atuação.

Precisarão apenas de variantes de conteúdo, não de nova linguagem visual:
`HeroSection` para nome e resumo do jogador; `PlayerPerformanceTable` e
`PlayerPerformanceCard` para agregados e partidas recentes. A lista lateral de
Partidas permanece um padrão de página, não um componente do Design System.

## Arquitetura da experiência

```text
Visão Geral
├── Partidas
│   ├── Detalhe
│   └── Comparação contextual
├── Jogadores
│   └── Perfil
└── História do Clube
    ├── Recordes
    ├── Marcos
    ├── Sequências
    └── Líderes
```

Configurações não faz parte da navegação narrativa e deve permanecer visualmente
separada.

## Hierarquia da informação

1. História: o que aconteceu e por que importa.
2. Protagonistas: quem participou.
3. Métricas: quais números sustentam a história.
4. Evidências: como a decisão foi produzida.

## Direção das telas

### Visão Geral

Porta de entrada editorial para a última partida, protagonistas, histórias e
descobertas históricas. Operação e compartilhamento são secundários.

### Partidas

Centro da experiência. Reúne lista cronológica, detalhe narrativo, jogadores,
premiações, estatísticas e evidências. Comparação é uma forma contextual de
explorar partidas, não uma entidade principal.

### Jogadores

Apresenta trajetória, desempenho, reconhecimentos e partidas recentes, sempre
conectando o jogador às partidas em que sua história foi construída.

### História do Clube

Transforma os insights determinísticos existentes em recordes, marcos,
sequências, líderes e fatos históricos exploráveis.

## Plano incremental aprovado

1. Fundação Visual e AppShell.
2. Transformar Histórico em Partidas.
3. Integrar Jogadores e Partidas.
4. Incorporar Comparação em Partidas.
5. Transformar Insights em História do Clube.
6. Redefinir a Visão Geral.
7. Consolidar responsividade e acessibilidade.
8. Remover duplicações após validação.

Cada etapa deve preservar rotas, contratos e comportamento até que uma mudança
deliberada seja aprovada.
