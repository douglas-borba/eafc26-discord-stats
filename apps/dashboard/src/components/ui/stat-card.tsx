import { Panel } from "./panel";

export function StatCard({ label, value, className }: { label: string; value: string | number; className?: string }) {
  return (
    <Panel className={className}>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">{label}</p>
      <p className="text-2xl font-bold text-text-primary">{value}</p>
    </Panel>
  );
}
