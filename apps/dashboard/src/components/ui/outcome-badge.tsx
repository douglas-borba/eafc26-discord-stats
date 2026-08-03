import { cn, outcomeLabel, outcomeBgColor } from "@/lib/utils";

export function OutcomeBadge({ outcome, className }: { outcome: "WIN" | "DRAW" | "LOSS"; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center justify-center w-6 h-6 rounded text-[11px] font-bold text-white",
        outcomeBgColor(outcome),
        className
      )}
    >
      {outcomeLabel(outcome)}
    </span>
  );
}
