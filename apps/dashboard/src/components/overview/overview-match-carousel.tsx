"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { MatchSummaryPresentation } from "@/lib/services/match-card-service";
import { OverviewMatchCard } from "./overview-match-card";

interface Props {
  presentations: MatchSummaryPresentation[];
}

const CARDS_PER_PAGE = 3;

export function OverviewMatchCarousel({ presentations }: Props) {
  const [currentPage, setCurrentPage] = useState(0);
  const [isTransitioning, setIsTransitioning] = useState(false);

  const totalPages = Math.ceil(presentations.length / CARDS_PER_PAGE);
  const canGoBack = currentPage > 0;
  const canGoForward = currentPage < totalPages - 1;

  const handlePrevious = (e: React.MouseEvent) => {
    e.preventDefault();
    if (!canGoBack || isTransitioning) return;
    setIsTransitioning(true);
    setCurrentPage((prev) => prev - 1);
    setTimeout(() => setIsTransitioning(false), 400);
  };

  const handleNext = (e: React.MouseEvent) => {
    e.preventDefault();
    if (!canGoForward || isTransitioning) return;
    setIsTransitioning(true);
    setCurrentPage((prev) => prev + 1);
    setTimeout(() => setIsTransitioning(false), 400);
  };

  const startIndex = currentPage * CARDS_PER_PAGE;
  const visibleCards = presentations.slice(startIndex, startIndex + CARDS_PER_PAGE);

  return (
    <>
      {/* Desktop: Netflix-style carousel with side arrows */}
      <div className="hidden md:block relative">
        <div className="flex items-center gap-3 p-5 lg:p-6">
          {/* Left arrow */}
          {canGoBack && (
            <button
              onClick={handlePrevious}
              disabled={isTransitioning}
              className="flex-shrink-0 flex items-center justify-center w-12 h-12 rounded-full bg-[#21262d]/90 border border-[#30363d] hover:bg-[#30363d] hover:scale-110 disabled:opacity-30 disabled:cursor-not-allowed transition-all backdrop-blur-sm shadow-lg z-10"
              aria-label="Página anterior"
            >
              <ChevronLeft className="w-6 h-6 text-[#e6edf3]" />
            </button>
          )}

          {/* Cards container with slide animation */}
          <div className="flex-1 overflow-hidden">
            <div
              className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5 transition-transform duration-400 ease-in-out"
              style={{
                transform: `translateX(0)`,
              }}
            >
              {visibleCards.map((p, i) => (
                <OverviewMatchCard
                  key={p.matchId}
                  presentation={p}
                  variant="full"
                  isLatest={startIndex + i === 0}
                />
              ))}
            </div>
          </div>

          {/* Right arrow */}
          {canGoForward && (
            <button
              onClick={handleNext}
              disabled={isTransitioning}
              className="flex-shrink-0 flex items-center justify-center w-12 h-12 rounded-full bg-[#21262d]/90 border border-[#30363d] hover:bg-[#30363d] hover:scale-110 disabled:opacity-30 disabled:cursor-not-allowed transition-all backdrop-blur-sm shadow-lg z-10"
              aria-label="Próxima página"
            >
              <ChevronRight className="w-6 h-6 text-[#e6edf3]" />
            </button>
          )}
        </div>
      </div>

      {/* Mobile: All cards in vertical scroll */}
      <div className="md:hidden flex flex-col gap-5 p-5">
        {presentations.map((p, i) => (
          <OverviewMatchCard
            key={p.matchId}
            presentation={p}
            variant="full"
            isLatest={i === 0}
          />
        ))}
      </div>
    </>
  );
}

