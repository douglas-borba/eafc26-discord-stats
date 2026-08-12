"use client";
import { useState } from "react";
export function TrialRequestForm() {
  const [state, setState] = useState<"idle" | "loading" | "success" | "error">("idle");
  async function submit(form: FormData) {
    setState("loading");
    const response = await fetch("/api/trial-requests", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ clubName: form.get("clubName"), requesterName: form.get("requesterName"), contact: form.get("contact") }) });
    setState(response.ok ? "success" : "error");
  }
  if (state === "success") return <p className="landing-trial-feedback landing-trial-success">Solicitação enviada. Em breve entraremos em contato.</p>;
  return <form action={submit} className="landing-trial-form">
    <input required maxLength={160} name="clubName" placeholder="Nome completo do clube" aria-label="Nome completo do clube" />
    <input required maxLength={160} name="requesterName" placeholder="Seu nome" aria-label="Seu nome" />
    <input required maxLength={320} name="contact" placeholder="WhatsApp, Discord ou e-mail" aria-label="WhatsApp, Discord ou e-mail" />
    <p>Usaremos seu contato somente para responder à solicitação do teste.</p>
    <button type="submit" className="landing-btn-primary landing-btn-lg" disabled={state === "loading"}>{state === "loading" ? "Enviando…" : "Testar grátis por 3 partidas"}</button>
    {state === "error" && <p className="landing-trial-feedback">Não foi possível enviar agora. Tente novamente.</p>}
  </form>;
}
