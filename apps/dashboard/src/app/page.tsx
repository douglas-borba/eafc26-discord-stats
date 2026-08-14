import type { Metadata } from "next";
import Image from "next/image";
import "./landing.css";
import { TrialRequestForm } from "@/components/landing/trial-request-form";
import { OverviewShowcase } from "@/components/landing/overview-showcase";

export const metadata: Metadata = {
  title: "Club11 — O acompanhamento do seu clube no Pro Clubs",
  description:
    "Artilheiros, destaques, campanha e os protagonistas do seu clube no Pro Clubs — registrados automaticamente a cada partida.",
  openGraph: {
    title: "Club11 — O acompanhamento do seu clube no Pro Clubs",
    description:
      "Artilheiros, destaques, campanha e os protagonistas do seu clube no Pro Clubs — registrados automaticamente a cada partida.",
    type: "website",
  },
};

export default function Home() {
  return (
    <main className="landing">
      <Header />
      <Hero />
      <ProductProof />
      <WhatItReveals />
      <HowItWorks />
      <Discord />
      <FinalCTA />
      <Footer />
    </main>
  );
}

function Header() {
  return (
    <header className="landing-header">
      <a href="#inicio" className="landing-logo" aria-label="Club11, início">
        <span className="landing-logo-mark">11</span>
        <span className="landing-logo-text">CLUB11</span>
      </a>
      <nav aria-label="Navegação principal" className="landing-nav">
        <a href="#o-que-revela">O que revela</a>
        <a href="#como-funciona">Como funciona</a>
      </nav>
      <a href="#quero" className="landing-header-cta">
        Quero ver meu clube
      </a>
    </header>
  );
}

function Hero() {
  return (
    <section id="inicio" className="landing-hero">
      <div className="landing-hero-glow" aria-hidden="true" />
      <div className="landing-hero-grid">
        <div className="landing-hero-copy">
          <p className="landing-descriptor">O acompanhamento do seu clube no Pro Clubs</p>
          <h1 className="landing-h1">
            Todo mundo acha que joga muito.
            <br />
            <span className="landing-h1-accent">Agora dá pra provar.</span>
          </h1>
          <p className="landing-hero-sub">
            Artilheiros, destaques, campanha e os protagonistas do seu clube no
            Pro Clubs — registrados automaticamente a cada partida.
          </p>
          <div className="landing-hero-actions">
            <a href="#quero" className="landing-btn-primary">
              Quero ver meu clube
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                aria-hidden="true"
              >
                <path
                  d="M6 3l5 5-5 5"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </a>
            <a href="#como-funciona" className="landing-btn-ghost">
              Ver como funciona
            </a>
          </div>
        </div>
        <div className="landing-hero-preview" aria-label="Dados reais de um clube acompanhado pelo Club11">
          <OverviewShowcase />
        </div>
      </div>
    </section>
  );
}

function ProductProof() {
  return (
    <section className="landing-proof">
      <div className="landing-section-inner">
        <div className="landing-proof-header">
          <h2 className="landing-h2">Isso é o que seu clube recebe.</h2>
          <p className="landing-proof-text">
            Uma prévia real com as partidas recentes do seu clube. Placar,
            desempenho, campanha e jogadores em destaque. Tudo com dados reais.
          </p>
        </div>
      </div>
    </section>
  );
}

function WhatItReveals() {
  return (
    <section id="o-que-revela" className="landing-reveals">
      <div className="landing-section-inner">
        <h2 className="landing-h2 landing-reveals-title">
          Cada partida revela quem é quem.
        </h2>
        <div className="landing-reveals-grid">
          <RevealBlock
            title="Artilheiros e garçons"
            text="Quem faz gol, quem dá assistência, quem aparece quando o jogo aperta."
          />
          <RevealBlock
            title="Craque e bagre"
            text="O melhor e o pior de cada partida. Com nota, com nome, sem piedade."
          />
          <RevealBlock
            title="Campanha e fases"
            text="Sequência de vitórias, fase ruim, aproveitamento geral. A história do clube em resultados."
          />
          <RevealBlock
            title="Adversários"
            text="Contra quem vocês jogam bem. Contra quem vocês sofrem. Tudo registrado."
          />
        </div>
      </div>
    </section>
  );
}

function RevealBlock({ title, text }: { title: string; text: string }) {
  return (
    <div className="landing-reveal">
      <h3 className="landing-reveal-title">{title}</h3>
      <p className="landing-reveal-text">{text}</p>
    </div>
  );
}

function HowItWorks() {
  const steps = [
    {
      title: "Você pede",
      text: "Informa o nome do clube.",
    },
    {
      title: "A gente prepara",
      text: "Localizamos o clube e montamos uma prévia com as partidas recentes reais.",
    },
    {
      title: "Seu clube aparece",
      text: "Você recebe o link com os dados do seu clube. Mostra pra galera.",
    },
  ];

  return (
    <section id="como-funciona" className="landing-how">
      <div className="landing-section-inner">
        <h2 className="landing-h2 landing-how-title">
          Sem instalar. Sem configurar. Sem lembrar de nada.
        </h2>
        <div className="landing-steps">
          {steps.map((s) => (
            <div key={s.title} className="landing-step">
              <h3 className="landing-step-title">{s.title}</h3>
              <p className="landing-step-text">{s.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Discord() {
  return (
    <section className="landing-discord">
      <div className="landing-section-inner landing-discord-inner">
        <div className="landing-discord-copy">
          <h2 className="landing-h2">
            Depois do jogo, o resumo chega no Discord.
          </h2>
          <p className="landing-discord-text">
            Com o acompanhamento ativo, cada partida vira um resumo completo —
            placar, gols, assistências, destaques e craque — publicado direto no
            canal do clube.
          </p>
        </div>
        <div className="landing-discord-card">
          <div className="landing-discord-frame">
            <Image
              src="/landing/batista-flores-match-card.png"
              alt="Resumo de partida real publicado no Discord — Associação BF 4 × 2 JardimHelenaFC"
              width={420}
              height={898}
              className="landing-discord-img"
              priority={false}
            />
          </div>
        </div>
      </div>
    </section>
  );
}

function FinalCTA() {
  return (
    <section id="quero" className="landing-final-cta">
      <div className="landing-section-inner">
        <div className="landing-final-cta-box">
          <h2 className="landing-h2 landing-final-cta-title">
            Seu clube também tem uma história.
            <br />
            <span className="landing-h2-accent">A gente mostra.</span>
          </h2>
          <p className="landing-final-cta-sub">
            Informe o nome do clube e a gente prepara uma prévia com os dados
            reais das partidas recentes.
          </p>
          <TrialRequestForm />
        </div>
      </div>
    </section>
  );
}

function Footer() {
  return (
    <footer className="landing-footer">
      <div className="landing-section-inner landing-footer-inner">
        <div className="landing-footer-brand">
          <span className="landing-logo-mark landing-logo-mark-sm">11</span>
          <span className="landing-logo-text">CLUB11</span>
        </div>
        <p className="landing-footer-legal">
          © {new Date().getFullYear()} Club11 · EA SPORTS e EA SPORTS FC são
          marcas da Electronic Arts. Este projeto não é afiliado ou endossado
          pela Electronic Arts.
        </p>
      </div>
    </footer>
  );
}
