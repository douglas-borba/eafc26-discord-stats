"use client";

import { Search } from "lucide-react";

export function SearchInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="relative">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted" />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder ?? "Buscar..."}
        className="w-full pl-9 pr-3 py-2 bg-surface-raised border border-border rounded-lg text-sm text-text-primary placeholder:text-muted focus:outline-none focus:border-accent"
      />
    </div>
  );
}
