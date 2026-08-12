import Link from "next/link";
export function TrialNotice({ status, count, limit }: { status?: string; count?: number | null; limit?: number | null }) {
  if (status === "ACTIVE" || !status) return null;
  if (status === "TRIAL_EXPIRED") return <div className="mb-4 rounded-lg border border-yellow-500/30 bg-yellow-500/10 p-3 text-sm text-text-soft"><strong className="text-text-primary">Seu teste terminou.</strong> Você acompanhou {limit ?? 3} partidas com o FC Stats. <CommercialLink /></div>;
  return <div className="mb-4 rounded-lg border border-accent/30 bg-accent/10 p-3 text-sm text-text-soft"><strong className="text-text-primary">Teste gratuito</strong> · {count ?? 0} de {limit ?? 3} partidas acompanhadas</div>;
}
export function UpgradePrompt() { return <section className="mx-auto max-w-xl py-16 text-center"><p className="text-xs font-semibold uppercase tracking-widest text-accent">FC Stats completo</p><h1 className="mt-3 text-2xl font-semibold text-text-primary">Disponível no FC Stats completo</h1><p className="mt-3 text-text-soft">Durante o teste gratuito, você pode acompanhar o Overview do seu clube por 3 partidas. Ative o FC Stats para acessar histórico, jogadores, adversários e Discord.</p><div className="mt-6"><CommercialLink /></div></section>; }
function CommercialLink() { const href = process.env.COMMERCIAL_CONTACT_URL?.trim(); return href ? <a className="text-accent underline underline-offset-4" href={href}>Quero continuar usando</a> : <Link className="text-accent underline underline-offset-4" href="/">Quero continuar usando</Link>; }
