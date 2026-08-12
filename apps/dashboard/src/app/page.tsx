import type { Metadata } from "next";
import Image from "next/image";
import "./landing.css";

export const metadata: Metadata = {
  title: "FC Stats | Estatísticas para EA SPORTS FC Clubs",
  description:
    "Dashboard automático de estatísticas, desempenho e partidas para clubes de EA SPORTS FC.",
  openGraph: {
    title: "FC Stats | Estatísticas para EA SPORTS FC Clubs",
    description:
      "Dashboard automático de estatísticas, desempenho e partidas para clubes de EA SPORTS FC.",
    type: "website",
  },
};

export default function Home() {
  return (
    <main className="landing">
      <Header />
      <Hero />
      <Benefits />
      <HowItWorks />
      <MatchCardShowcase />
      <FinalCTA />
      <Footer />
    </main>
  );
}

function Header() {
  return (
    <header className="landing-header">
      <a href="#inicio" className="landing-logo" aria-label="FC Stats, início">
        <span className="landing-logo-mark">FC</span>
        <span className="landing-logo-text">STATS</span>
      </a>
      <nav aria-label="Navegação principal" className="landing-nav">
        <a href="#recursos">Recursos</a>
        <a href="#como-funciona">Como funciona</a>
      </nav>
      <a href="#contato" className="landing-header-cta">
        Quero acompanhar meu clube
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
          <h1 className="landing-h1">
            Seu Pro Clubs.
            <br />
            Seus números.
            <br />
            <span className="landing-h1-accent">Sua história.</span>
          </h1>
          <p className="landing-hero-sub">
            Estatísticas, desempenho e destaques do seu clube no EA SPORTS FC,
            atualizados automaticamente após cada partida.
          </p>
          <div className="landing-hero-actions">
            <a href="#contato" className="landing-btn-primary">
              Quero acompanhar meu clube
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
        <div className="landing-hero-preview" aria-label="Prévia ilustrativa do dashboard">
          <ProductMockup />
        </div>
      </div>
    </section>
  );
}

function ProductMockup() {
  return (
    <div className="mockup">
      <div className="mockup-header">
        <span className="mockup-brand">FC STATS</span>
        <span className="mockup-club-name">CLUBE EXEMPLO</span>
      </div>

      <div className="mockup-match">
        <div className="mockup-match-label">ÚLTIMA PARTIDA</div>
        <div className="mockup-score-row">
          <span className="mockup-team">Clube Exemplo</span>
          <div className="mockup-score">
            <span className="mockup-score-num">4</span>
            <span className="mockup-score-sep">×</span>
            <span className="mockup-score-num mockup-score-opp">2</span>
          </div>
          <span className="mockup-team">Adversário</span>
        </div>
        <div className="mockup-result-badge">Vitória</div>
      </div>

      <div className="mockup-stats-row">
        <div className="mockup-stat">
          <span className="mockup-stat-value">68%</span>
          <span className="mockup-stat-label">Aproveitamento</span>
        </div>
        <div className="mockup-stat">
          <span className="mockup-stat-value mockup-stat-record">
            <span className="mockup-w">12V</span>
            <span className="mockup-d">3E</span>
            <span className="mockup-l">5D</span>
          </span>
          <span className="mockup-stat-label">Campanha</span>
        </div>
      </div>

      <div className="mockup-highlights">
        <div className="mockup-highlight">
          <div className="mockup-hl-header">
            <span className="mockup-hl-icon">★</span>
            <span className="mockup-hl-title">Craque</span>
          </div>
          <span className="mockup-hl-name">Jogador 10</span>
          <span className="mockup-hl-rating">9.1</span>
        </div>
        <div className="mockup-highlight">
          <div className="mockup-hl-header">
            <span className="mockup-hl-icon">⚽</span>
            <span className="mockup-hl-title">Artilheiro</span>
          </div>
          <span className="mockup-hl-name">Jogador 9</span>
          <span className="mockup-hl-rating">18 gols</span>
        </div>
      </div>

      <div className="mockup-form-row">
        {["V", "V", "D", "V", "V"].map((r, i) => (
          <span
            key={i}
            className={`mockup-form-pip ${r === "V" ? "mockup-pip-w" : r === "E" ? "mockup-pip-d" : "mockup-pip-l"}`}
          >
            {r}
          </span>
        ))}
      </div>
    </div>
  );
}

function Benefits() {
  return (
    <section id="recursos" className="landing-benefits">
      <div className="landing-section-inner">
        <div className="landing-benefits-header">
          <h2 className="landing-h2">
            Tudo que acontece em campo,
            <br />
            <span className="landing-h2-accent">registrado automaticamente.</span>
          </h2>
        </div>
        <div className="landing-benefits-grid">
          <BenefitItem
            title="Estatísticas automáticas"
            text="Resultados, gols, assistências, notas e desempenho atualizados após as partidas."
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 3v18h18" />
                <path d="M7 16l4-8 4 4 5-9" />
              </svg>
            }
          />
          <BenefitItem
            title="Destaques das partidas"
            text="Craque, Bagre e os principais acontecimentos de cada jogo."
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
              </svg>
            }
          />
          <BenefitItem
            title="Histórico e evolução"
            text="Acompanhe partidas, adversários, jogadores e a evolução do clube."
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <polyline points="12 6 12 12 16 14" />
              </svg>
            }
          />
          <BenefitItem
            title="Discord"
            text="Os resultados e destaques chegam ao Discord do clube sem trabalho manual."
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z" />
              </svg>
            }
          />
        </div>
      </div>
    </section>
  );
}

function BenefitItem({
  title,
  text,
  icon,
}: {
  title: string;
  text: string;
  icon: React.ReactNode;
}) {
  return (
    <div className="landing-benefit">
      <div className="landing-benefit-icon">{icon}</div>
      <h3 className="landing-benefit-title">{title}</h3>
      <p className="landing-benefit-text">{text}</p>
    </div>
  );
}

function HowItWorks() {
  const steps = [
    { num: "1", title: "Cadastramos seu clube", text: "Encontramos o clube e preparamos o acompanhamento." },
    { num: "2", title: "Vocês jogam normalmente", text: "Sem configurar nada, sem lembrar de gravar." },
    { num: "3", title: "O FC Stats acompanha as partidas", text: "Resultados e atuações registrados automaticamente." },
    { num: "4", title: "Dashboard e Discord atualizados", text: "O time acompanha cada capítulo da temporada." },
  ];
  return (
    <section id="como-funciona" className="landing-how">
      <div className="landing-section-inner">
        <h2 className="landing-h2 landing-how-title">Como funciona</h2>
        <p className="landing-how-sub">
          Seu clube joga. A história fica registrada.
        </p>
        <div className="landing-steps">
          {steps.map((s, i) => (
            <div key={s.num} className="landing-step">
              <div className="landing-step-num">{s.num}</div>
              <h3 className="landing-step-title">{s.title}</h3>
              <p className="landing-step-text">{s.text}</p>
              {i < steps.length - 1 && (
                <div className="landing-step-connector" aria-hidden="true" />
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function MatchCardShowcase() {
  return (
    <section className="landing-showcase">
      <div className="landing-section-inner landing-showcase-inner">
        <div className="landing-showcase-copy">
          <h2 className="landing-h2">
            Da partida
            <br />
            <span className="landing-h2-accent">para o Discord.</span>
          </h2>
          <p className="landing-showcase-text">
            Depois do jogo, o FC Stats transforma o resultado em um resumo
            completo — placar, gols, assistências, destaques individuais,
            craque da partida e insights automáticos — publicado direto
            no canal do Discord do clube.
          </p>
          <p className="landing-showcase-label">
            Partida real processada pelo FC Stats.
          </p>
        </div>
        <div className="landing-showcase-card">
          <div className="landing-showcase-frame">
            <Image
              src="/landing/batista-flores-match-card.png"
              alt="Match card real gerado pelo FC Stats — Associação BF 4 × 2 JardimHelenaFC"
              width={420}
              height={898}
              className="landing-showcase-img"
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
    <section id="contato" className="landing-final-cta">
      <div className="landing-section-inner">
        <div className="landing-final-cta-box">
          <h2 className="landing-h2 landing-final-cta-title">
            Seu clube também pode ter uma história contada em números.
          </h2>
          <a href="#inicio" className="landing-btn-primary landing-btn-lg">
            Quero acompanhar meu clube
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
          <span className="landing-logo-mark landing-logo-mark-sm">FC</span>
          <span className="landing-logo-text">STATS</span>
        </div>
        <p className="landing-footer-legal">
          © {new Date().getFullYear()} FC Stats · EA SPORTS e EA SPORTS FC são
          marcas da Electronic Arts. Este projeto não é afiliado ou endossado
          pela Electronic Arts.
        </p>
      </div>
    </footer>
  );
}
