/**
 * Promotional snapshot captured from real processed Club11 data.
 * Source club: Associação BF.
 * Values in this file originate from the real canonical/editorial pipeline
 * and must not be replaced with fictional demo values.
 *
 * Captured: 2026-08-14
 * Cards: 968624156790107 (5×4 vs Bagulho), 960632703180174 (4×2 vs JardimHelenaFC)
 */

import type { SequenceEditorial } from "@/lib/services/sequence-editorial-service";
import type { MatchSummaryPresentation } from "@/lib/services/match-card-service";

export const SHOWCASE_CLUB_NAME = "Associação BF";

export const SHOWCASE_EDITORIAL: SequenceEditorial = {
  "title": "Momento de atenção",
  "subtitle": "As últimas 10 partidas",
  "narrative": "Associação BF alterna resultados nas últimas partidas. R. Nazario é o destaque ofensivo com 6 gols no período. 1paulorodrig apareceu 4 vezes entre os destaques.",
  "aiNarrative": null,
  "stats": {
    "wins": 2,
    "draws": 4,
    "losses": 4,
    "goalsScored": 16,
    "goalsConceded": 21,
    "goalDifference": -5,
    "matchCount": 10,
    "avgGoalsScored": "1.6",
    "pointsPercentage": "33.3"
  },
  "matchDetails": [
    {
      "matchId": "968624156790107",
      "date": "14 ago. 2026 • 01:31",
      "opponent": "Bagulho",
      "ourScore": 5,
      "oppScore": 4,
      "outcome": "WIN"
    },
    {
      "matchId": "968630046210194",
      "date": "14 ago. 2026 • 01:10",
      "opponent": "Low Cortisol",
      "ourScore": 0,
      "oppScore": 3,
      "outcome": "LOSS"
    },
    {
      "matchId": "968560055670111",
      "date": "14 ago. 2026 • 00:55",
      "opponent": "1590 coca lata",
      "ourScore": 1,
      "oppScore": 2,
      "outcome": "LOSS"
    },
    {
      "matchId": "968316881830376",
      "date": "14 ago. 2026 • 00:37",
      "opponent": "Arranca  Toco",
      "ourScore": 1,
      "oppScore": 1,
      "outcome": "DRAW"
    },
    {
      "matchId": "968234905440345",
      "date": "14 ago. 2026 • 00:19",
      "opponent": "TROPA DO BABIDI",
      "ourScore": 1,
      "oppScore": 1,
      "outcome": "DRAW"
    },
    {
      "matchId": "968350539570304",
      "date": "14 ago. 2026 • 00:02",
      "opponent": "TROPA DO BABIDI",
      "ourScore": 2,
      "oppScore": 2,
      "outcome": "DRAW"
    },
    {
      "matchId": "968280484510012",
      "date": "13 ago. 2026 • 23:40",
      "opponent": "RPZ da Leste",
      "ourScore": 0,
      "oppScore": 1,
      "outcome": "LOSS"
    },
    {
      "matchId": "968342239100119",
      "date": "13 ago. 2026 • 23:23",
      "opponent": "ObgMiscigenação",
      "ourScore": 1,
      "oppScore": 4,
      "outcome": "LOSS"
    },
    {
      "matchId": "960632703180174",
      "date": "11 ago. 2026 • 19:46",
      "opponent": "JardimHelenaFC",
      "ourScore": 4,
      "oppScore": 2,
      "outcome": "WIN"
    },
    {
      "matchId": "960582639010302",
      "date": "11 ago. 2026 • 19:27",
      "opponent": "BRAUNAS FC",
      "ourScore": 1,
      "oppScore": 1,
      "outcome": "DRAW"
    }
  ],
  "topScorer": {
    "name": "R. Nazario",
    "goals": 6
  },
  "topAssister": {
    "name": "Ronaldinho",
    "assists": 7
  },
  "topHighlight": {
    "name": "1paulorodrig",
    "appearances": 4
  },
  "topRatedPlayer": {
    "name": "1paulorodrig",
    "avgRating": "8.11"
  },
  "currentStreak": {
    "type": "WIN",
    "count": 1,
    "label": "1 vitória"
  }
} as const;

export const SHOWCASE_CARDS: MatchSummaryPresentation[] = [
  {
    "date": "14 ago. 2026 • 01:31",
    "bagre": {
      "name": "Beckham",
      "phrase": "Estava em todos os duelos. Saiu de poucos.",
      "rating": "6,70",
      "reason": "Menor nota entre os jogadores elegíveis.",
      "passStats": null,
      "tackleStats": "0/3 certos (0%)"
    },
    "goals": {
      "scorers": [
        {
          "name": "R. Nazario",
          "count": 2
        },
        {
          "name": "NEYMAR",
          "count": 2
        },
        {
          "name": "Ibrahimovic",
          "count": 1
        }
      ]
    },
    "craque": {
      "name": "NEYMAR",
      "phrase": "A defesa vai sonhar com ele.",
      "reason": "Nota 10,00 • 2 gols e 2 assistências • Craque da Partida (EA)"
    },
    "xerife": null,
    "assists": {
      "assisters": [
        {
          "name": "NEYMAR",
          "count": 2
        },
        {
          "name": "D. Prima",
          "count": 1
        }
      ]
    },
    "matchId": "968624156790107",
    "muralha": null,
    "oppName": "Bagulho",
    "ourName": "Associação BF",
    "outcome": {
      "type": "WIN",
      "color": 3066993,
      "emoji": "🟢",
      "label": "Vitória"
    },
    "redCard": null,
    "oppScore": 4,
    "ourScore": 5,
    "timestamp": "2026-08-14T04:31:58Z",
    "allPlayers": [
      {
        "name": "Ronaldinho",
        "played": true,
        "rating": "3,00"
      },
      {
        "name": "R. Nazario",
        "played": true,
        "rating": "8,90"
      },
      {
        "name": "Ibrahimovic",
        "played": true,
        "rating": "7,10"
      },
      {
        "name": "NEYMAR",
        "played": true,
        "rating": "10,00"
      },
      {
        "name": "D. Prima",
        "played": true,
        "rating": "7,10"
      },
      {
        "name": "Beckham",
        "played": true,
        "rating": "6,70"
      }
    ],
    "highlights": {
      "top3": [
        {
          "name": "NEYMAR",
          "medal": "🥇",
          "rating": "10,00"
        },
        {
          "name": "R. Nazario",
          "medal": "🥈",
          "rating": "8,90"
        },
        {
          "name": "Ibrahimovic",
          "medal": "🥉",
          "rating": "7,10"
        }
      ],
      "teamAverage": "7,13"
    },
    "passePrecisao": {
      "name": "NEYMAR",
      "phrase": "O mapa do campo estava na cabeça dele.",
      "accuracy": 94,
      "passesMade": 18,
      "passAttempts": 19
    },
    "correioExtraviado": null,
    "offensiveNarratives": [
      {
        "name": "Ibrahimovic",
        "emoji": "😬",
        "goals": 1,
        "shots": 5,
        "title": "FICOU NO QUASE",
        "message": "Criou bastante, mas faltou transformar mais chances em gol."
      }
    ]
  },
  {
    "date": "11 ago. 2026 • 19:46",
    "bagre": {
      "name": "NEYMAR",
      "phrase": "Hoje a marcação foi mais sugestão do que realidade.",
      "rating": "6,90",
      "reason": "Menor nota entre os jogadores elegíveis.",
      "passStats": "9/13 certos (69%) · 4 errados",
      "tackleStats": "2/5 certos (40%)"
    },
    "goals": {
      "scorers": [
        {
          "name": "R. Nazario",
          "count": 2
        },
        {
          "name": "ian silva",
          "count": 1
        },
        {
          "name": "Beckham",
          "count": 1
        }
      ]
    },
    "craque": {
      "name": "Ronaldinho",
      "phrase": "Lembrou a todos por que é craque.",
      "reason": "Nota 9,20 • 3 assistências • Craque da Partida (EA)"
    },
    "xerife": null,
    "assists": {
      "assisters": [
        {
          "name": "Ronaldinho",
          "count": 3
        },
        {
          "name": "R. Nazario",
          "count": 1
        }
      ]
    },
    "matchId": "960632703180174",
    "muralha": null,
    "oppName": "JardimHelenaFC",
    "ourName": "Associação BF",
    "outcome": {
      "type": "WIN",
      "color": 3066993,
      "emoji": "🟢",
      "label": "Vitória"
    },
    "redCard": null,
    "oppScore": 2,
    "ourScore": 4,
    "timestamp": "2026-08-11T19:46:29Z",
    "allPlayers": [
      {
        "name": "Ronaldinho",
        "played": true,
        "rating": "9,20"
      },
      {
        "name": "R. Nazario",
        "played": true,
        "rating": "9,10"
      },
      {
        "name": "WHITE",
        "played": true,
        "rating": "7,50"
      },
      {
        "name": "D. Oliveira",
        "played": true,
        "rating": "7,30"
      },
      {
        "name": "ian silva",
        "played": true,
        "rating": "7,10"
      },
      {
        "name": "NEYMAR",
        "played": true,
        "rating": "6,90"
      },
      {
        "name": "D. Prima",
        "played": true,
        "rating": "7,40"
      },
      {
        "name": "Beckham",
        "played": true,
        "rating": "8,10"
      },
      {
        "name": "Haalandinho",
        "played": true,
        "rating": "8,50"
      }
    ],
    "highlights": {
      "top3": [
        {
          "name": "Ronaldinho",
          "medal": "🥇",
          "rating": "9,20"
        },
        {
          "name": "R. Nazario",
          "medal": "🥈",
          "rating": "9,10"
        },
        {
          "name": "Haalandinho",
          "medal": "🥉",
          "rating": "8,50"
        }
      ],
      "teamAverage": "7,90"
    },
    "passePrecisao": {
      "name": "Beckham",
      "phrase": "Ligou o time inteiro com os pés.",
      "accuracy": 92,
      "passesMade": 13,
      "passAttempts": 14
    },
    "correioExtraviado": {
      "name": "Haalandinho",
      "phrase": "Os Correios ligaram para pedir dicas.",
      "deltaPct": 23,
      "missedPasses": 7,
      "teamAccuracyPct": 76,
      "playerAccuracyPct": 53
    },
    "offensiveNarratives": []
  }
] as unknown as MatchSummaryPresentation[];

