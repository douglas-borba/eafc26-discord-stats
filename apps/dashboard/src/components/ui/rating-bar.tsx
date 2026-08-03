import { ratingBarWidth, ratingColor } from "@/lib/utils";

export function RatingBar({ rating }: { rating: number }) {
  const width = ratingBarWidth(rating);
  const color = ratingColor(rating);
  return (
    <div className="flex items-center gap-2">
      <span className={`text-sm font-semibold ${color}`}>{rating.toFixed(1)}</span>
      <div className="flex-1 h-1 bg-border rounded-full overflow-hidden">
        <div className={`h-full rounded-full ${color.replace("text-", "bg-")}`} style={{ width: `${width}%` }} />
      </div>
    </div>
  );
}
