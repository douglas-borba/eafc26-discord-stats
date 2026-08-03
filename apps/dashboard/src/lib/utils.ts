import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleDateString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function outcomeColor(outcome: "WIN" | "DRAW" | "LOSS"): string {
  switch (outcome) {
    case "WIN": return "text-win";
    case "DRAW": return "text-draw";
    case "LOSS": return "text-loss";
  }
}

export function outcomeBgColor(outcome: "WIN" | "DRAW" | "LOSS"): string {
  switch (outcome) {
    case "WIN": return "bg-win";
    case "DRAW": return "bg-draw";
    case "LOSS": return "bg-loss";
  }
}

export function outcomeLabel(outcome: "WIN" | "DRAW" | "LOSS"): string {
  switch (outcome) {
    case "WIN": return "V";
    case "DRAW": return "E";
    case "LOSS": return "D";
  }
}

export function ratingColor(rating: number): string {
  if (rating >= 8.5) return "text-accent";
  if (rating >= 7.5) return "text-win";
  if (rating >= 6.5) return "text-[#7ee787]";
  if (rating >= 5.5) return "text-draw";
  return "text-loss";
}

export function ratingBarWidth(rating: number): number {
  return Math.max(0, Math.min(100, ((rating - 4) / 6) * 100));
}
