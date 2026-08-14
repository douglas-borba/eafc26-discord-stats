"use client";
import { useState } from "react";
export function TrialRequestForm() {
  const [state, setState] = useState<"idle" | "loading" | "success" | "error">("idle");
  async function submit(form: FormData) {
    setState("loading");
    const response = await fetch("/api/trial-requests", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ clubName: form.get("clubName"), requesterName: form.get("requesterName"), contact: form.get("contact") }) });
    setState(response.ok ? "success" : "error");
  }
  if (state === "success") return <p className="landing-trial-feedback landing-trial-success">Recebemos. Vamos localizar seu clube e preparar a prévia com os dados reais. A gente avisa assim que ficar pronto.</p>;
  return <form action={submit} className="landing-trial-form">
    <input required maxLength={160} name="clubName" placeholder="Ex: Associação BF" aria-label="Nome do clube" />
    <input required maxLength={160} name="requesterName" placeholder="Ex: Ronaldinho" aria-label="Seu nome ou gamertag" />
    <input required maxLength={320} name="contact" placeholder="WhatsApp, Discord ou e-mail" aria-label="Como a gente te responde?" />
    <p>Usamos seu contato só pra avisar quando a prévia ficar pronta.</p>
    <button type="submit" className="landing-btn-primary landing-btn-lg" disabled={state === "loading"}>{state === "loading" ? "Enviando…" : "Quero ver meu clube"}</button>
    {state === "error" && <p className="landing-trial-feedback">Não foi possível enviar. Tente de novo.</p>}
  </form>;
}
