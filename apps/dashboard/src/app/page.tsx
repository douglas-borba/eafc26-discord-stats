import type { Metadata } from "next";
import { BarChart3, ChevronRight, CircleCheck, Goal, Shield, Trophy, Users } from "lucide-react";

export const metadata: Metadata = {
  title: "FC Stats | Estatísticas para EA SPORTS FC Clubs",
  description: "Dashboard automático de estatísticas, desempenho e partidas para clubes de EA SPORTS FC.",
  openGraph: {
    title: "FC Stats | Estatísticas para EA SPORTS FC Clubs",
    description: "Dashboard automático de estatísticas, desempenho e partidas para clubes de EA SPORTS FC.",
    type: "website",
  },
};

const features = [
  {
    icon: BarChart3,
    title: "Estatísticas automáticas",
    text: "Resultados, gols, assistências, notas e desempenho dos jogadores atualizados automaticamente.",
  },
  {
    icon: Trophy,
    title: "Destaques das partidas",
    text: "Craque, melhores desempenhos e acontecimentos importantes de cada jogo.",
  },
  {
    icon: Users,
    title: "Histórico e evolução",
    text: "Acompanhe partidas, adversários, jogadores e a evolução do clube ao longo do tempo.",
  },
  {
    icon: Goal,
    title: "Integração com Discord",
    text: "Terminou a partida? O resumo pode ser publicado automaticamente no Discord do clube.",
  },
];

export default function Home() {
  return (
    <main className="min-h-screen overflow-hidden bg-bg text-text-primary">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
        <a href="#inicio" className="flex items-center gap-2 font-semibold tracking-tight" aria-label="FC Stats, início">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent-strong text-sm font-bold text-white">FC</span>
          <span>STATS</span>
        </a>
        <nav aria-label="Navegação principal" className="hidden items-center gap-7 text-sm text-muted md:flex">
          <a href="#recursos" className="transition-colors hover:text-text-primary">Recursos</a>
          <a href="#como-funciona" className="transition-colors hover:text-text-primary">Como funciona</a>
          <a href="#contato" className="transition-colors hover:text-text-primary">Contato</a>
        </nav>
        <a href="#contato" className="rounded-lg border border-accent/60 px-3.5 py-2 text-sm font-medium text-accent transition-colors hover:bg-accent/10">
          Quero acompanhar meu clube
        </a>
      </header>

      <section id="inicio" className="relative mx-auto max-w-6xl px-5 pb-20 pt-14 sm:px-8 sm:pb-28 sm:pt-20">
        <div className="absolute inset-x-1/4 top-0 -z-0 h-72 rounded-full bg-accent/10 blur-3xl" />
        <div className="relative grid items-center gap-12 lg:grid-cols-[1fr_0.9fr]">
          <div>
            <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-border bg-surface/80 px-3 py-1.5 text-xs font-medium text-text-soft">
              <span className="h-1.5 w-1.5 rounded-full bg-win" /> Para clubes de EA SPORTS FC
            </p>
            <h1 className="max-w-xl text-4xl font-semibold leading-[1.08] tracking-tight sm:text-5xl lg:text-6xl">
              Seu Pro Clubs.<br />Seus números.<br /><span className="text-accent">Sua história.</span>
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-text-soft sm:text-lg">
              Estatísticas, desempenho e destaques do seu clube no EA SPORTS FC, atualizados automaticamente após cada partida.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <a href="#contato" className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-accent-strong px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-accent">
                Quero acompanhar meu clube <ChevronRight className="h-4 w-4" />
              </a>
              <a href="#como-funciona" className="inline-flex min-h-11 items-center rounded-lg border border-border px-5 py-2.5 text-sm font-medium text-text-soft transition-colors hover:bg-surface-raised">
                Ver como funciona
              </a>
            </div>
          </div>
          <DashboardPreview />
        </div>
      </section>

      <section id="recursos" className="border-y border-border bg-surface/40">
        <div className="mx-auto max-w-6xl px-5 py-20 sm:px-8">
          <div className="max-w-2xl">
            <p className="text-sm font-semibold text-accent">TUDO EM UM SÓ LUGAR</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Acompanhe o que acontece dentro de campo.</h2>
          </div>
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {features.map(({ icon: Icon, title, text }) => (
              <article key={title} className="rounded-xl border border-border bg-surface p-5 shadow-card">
                <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent/10 text-accent"><Icon className="h-5 w-5" /></span>
                <h3 className="mt-5 font-semibold">{title}</h3>
                <p className="mt-2 text-sm leading-6 text-muted">{text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="como-funciona" className="mx-auto max-w-6xl px-5 py-20 sm:px-8">
        <div className="grid gap-10 lg:grid-cols-[0.8fr_1fr] lg:items-start">
          <div>
            <p className="text-sm font-semibold text-accent">COMO FUNCIONA</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Seu clube joga. A história fica registrada.</h2>
            <p className="mt-4 max-w-md leading-7 text-muted">Uma experiência simples para transformar cada partida em acompanhamento real para o seu elenco.</p>
          </div>
          <ol className="space-y-3">
            {[
              ["1", "Cadastramos o clube", "Encontramos o clube correto e preparamos o acompanhamento."],
              ["2", "As partidas são monitoradas", "Os resultados e atuações passam a fazer parte do histórico."],
              ["3", "Dashboard e Discord atualizados", "O time acompanha cada capítulo da temporada no lugar certo."],
            ].map(([step, title, text]) => (
              <li key={step} className="flex gap-4 rounded-xl border border-border bg-surface p-5">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-accent-strong text-sm font-bold text-white">{step}</span>
                <div><h3 className="font-semibold">{title}</h3><p className="mt-1 text-sm leading-6 text-muted">{text}</p></div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="border-y border-border bg-surface/40">
        <div className="mx-auto grid max-w-6xl gap-10 px-5 py-20 sm:px-8 lg:grid-cols-[0.85fr_1.15fr] lg:items-center">
          <div>
            <p className="text-sm font-semibold text-accent">UMA VISÃO QUE FAZ SENTIDO</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Mais do que números: o contexto de cada partida.</h2>
            <p className="mt-4 max-w-md leading-7 text-muted">Placar, momentos, protagonistas e desempenho do elenco apresentados em uma leitura clara.</p>
            <ul className="mt-6 space-y-3 text-sm text-text-soft">
              {["Resultados e últimas partidas", "Desempenho do elenco", "Destaques que explicam o jogo"].map((item) => (
                <li key={item} className="flex items-center gap-2"><CircleCheck className="h-4 w-4 text-win" /> {item}</li>
              ))}
            </ul>
          </div>
          <DashboardPreview compact />
        </div>
      </section>

      <section id="contato" className="mx-auto max-w-6xl px-5 py-20 sm:px-8">
        <div className="rounded-2xl border border-accent/30 bg-gradient-to-br from-accent/15 via-surface to-surface p-8 sm:p-12">
          <Shield className="h-7 w-7 text-accent" />
          <h2 className="mt-5 max-w-xl text-3xl font-semibold tracking-tight sm:text-4xl">Seu clube também pode ter um dashboard assim.</h2>
          <p className="mt-4 max-w-xl leading-7 text-text-soft">Vamos preparar uma visão feita para acompanhar a jornada do seu elenco.</p>
          <a href="#inicio" className="mt-7 inline-flex min-h-11 items-center gap-2 rounded-lg bg-accent-strong px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-accent">
            Quero acompanhar meu clube <ChevronRight className="h-4 w-4" />
          </a>
        </div>
      </section>

      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-5 py-8 text-xs text-muted sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <div><span className="font-semibold text-text-soft">FC STATS</span><span className="mx-2">·</span>Estatísticas para clubes de EA SPORTS FC</div>
          <div>© {new Date().getFullYear()} FC Stats · EA SPORTS e EA SPORTS FC são marcas da Electronic Arts. Este projeto não é afiliado ou endossado pela Electronic Arts.</div>
        </div>
      </footer>
    </main>
  );
}

function DashboardPreview({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`relative rounded-2xl border border-border bg-[#11161e] p-3 shadow-card-featured ${compact ? "max-w-2xl" : "lg:translate-y-2"}`} aria-label="Prévia ilustrativa do dashboard">
      <div className="flex items-center justify-between border-b border-border px-2 pb-3 text-xs text-muted"><span className="font-semibold text-text-soft">FC STATS</span><span>Visão Geral</span></div>
      <div className="grid gap-3 p-2 pt-4 sm:grid-cols-[1.15fr_0.85fr]">
        <div className="rounded-xl border border-border bg-surface p-4">
          <p className="text-xs text-muted">ÚLTIMA PARTIDA</p>
          <div className="mt-4 flex items-center justify-between gap-3 text-center">
            <span className="text-sm font-semibold">Meu Clube</span>
            <strong className="text-3xl tracking-tight">3 <span className="text-muted">×</span> 1</strong>
            <span className="text-sm font-semibold">Adversário</span>
          </div>
          <div className="mt-4 rounded-lg bg-win/10 px-3 py-2 text-center text-xs font-medium text-win">Vitória · atuação coletiva consistente</div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <PreviewStat label="Aproveitamento" value="67%" />
          <PreviewStat label="Partidas" value="24" />
          <PreviewStat label="Gols" value="58" />
          <PreviewStat label="Média" value="7.4" />
        </div>
      </div>
      <div className="grid gap-3 p-2 sm:grid-cols-2">
        <div className="rounded-xl border border-border bg-surface p-4"><p className="text-xs text-muted">DESTAQUE DA PARTIDA</p><p className="mt-3 flex items-center gap-2 font-semibold"><span className="text-highlight">★</span> Craque</p><p className="mt-1 text-sm text-muted">Fez a diferença no resultado.</p></div>
        <div className="rounded-xl border border-border bg-surface p-4"><p className="text-xs text-muted">ÚLTIMAS PARTIDAS</p><div className="mt-3 flex gap-1.5">{["V", "V", "E", "D", "V"].map((outcome, index) => <span key={`${outcome}-${index}`} className={`flex h-6 w-6 items-center justify-center rounded text-xs font-bold ${outcome === "V" ? "bg-win/20 text-win" : outcome === "E" ? "bg-draw/20 text-draw" : "bg-loss/20 text-loss"}`}>{outcome}</span>)}</div><p className="mt-2 text-sm text-muted">Histórico recente do clube</p></div>
      </div>
    </div>
  );
}

function PreviewStat({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl border border-border bg-surface p-3"><p className="text-[11px] text-muted">{label}</p><p className="mt-1 text-lg font-semibold">{value}</p></div>;
}
