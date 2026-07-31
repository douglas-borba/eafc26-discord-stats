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
