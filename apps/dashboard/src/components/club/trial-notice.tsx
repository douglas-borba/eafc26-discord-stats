import Link from "next/link";
export function TrialNotice({ status }: { status?: string }) {
  if (status === "ACTIVE" || !status) return null;
  return <div className="mb-4 rounded-lg border border-accent/30 bg-accent/10 p-3 text-sm text-text-soft"><strong className="text-text-primary">Prévia gratuita</strong> · Este dashboard foi criado com as partidas recentes do seu clube. O acompanhamento automático não está ativo. <CommercialLink /></div>;
}
export function UpgradePrompt() { return <section className="mx-auto max-w-xl py-16 text-center"><p className="text-xs font-semibold uppercase tracking-widest text-accent">FC Stats completo</p><h1 className="mt-3 text-2xl font-semibold text-text-primary">Disponível no FC Stats completo</h1><p className="mt-3 text-text-soft">Esta é uma prévia do seu clube. Ative o acompanhamento para receber novas partidas e acessar histórico, jogadores, adversários e Discord.</p><div className="mt-6"><CommercialLink /></div></section>; }
function CommercialLink() { const href = process.env.COMMERCIAL_CONTACT_URL?.trim(); return href ? <a className="text-accent underline underline-offset-4" href={href}>Ativar acompanhamento</a> : <Link className="text-accent underline underline-offset-4" href="/">Ativar acompanhamento</Link>; }
