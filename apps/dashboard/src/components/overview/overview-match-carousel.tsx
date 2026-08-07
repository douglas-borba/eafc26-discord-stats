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

  const handlePrevious = () => {
    if (isTransitioning) return;
    setIsTransitioning(true);
    setCurrentPage((prev) => Math.max(0, prev - 1));
    setTimeout(() => setIsTransitioning(false), 300);
  };

  const handleNext = () => {
    if (isTransitioning) return;
    setIsTransitioning(true);
    setCurrentPage((prev) => Math.min(totalPages - 1, prev + 1));
    setTimeout(() => setIsTransitioning(false), 300);
  };

  const goToPage = (page: number) => {
    if (isTransitioning || page === currentPage) return;
    setIsTransitioning(true);
    setCurrentPage(page);
    setTimeout(() => setIsTransitioning(false), 300);
  };

  const startIndex = currentPage * CARDS_PER_PAGE;
  const visibleCards = presentations.slice(startIndex, startIndex + CARDS_PER_PAGE);

  return (
    <>
      {/* Desktop: Carousel with pagination */}
      <div className="hidden md:block">
        {/* Cards grid */}
        <div
          className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5 p-5 lg:p-6 items-start min-h-[600px] transition-opacity duration-300"
          style={{ opacity: isTransitioning ? 0.5 : 1 }}
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

        {/* Navigation controls */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 pb-6">
            <button
              onClick={handlePrevious}
              disabled={currentPage === 0 || isTransitioning}
              className="flex items-center justify-center w-10 h-10 rounded-full bg-[#21262d] border border-[#30363d] hover:bg-[#30363d] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              aria-label="Página anterior"
            >
              <ChevronLeft className="w-5 h-5 text-[#e6edf3]" />
            </button>

            {/* Page indicator */}
            <div className="flex items-center gap-2">
              {Array.from({ length: totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => goToPage(i)}
                  disabled={isTransitioning}
                  className={`rounded-full transition-all ${
                    i === currentPage
                      ? "bg-[#58a6ff] w-6 h-2"
                      : "bg-[#30363d] hover:bg-[#484f58] w-2 h-2"
                  }`}
                  aria-label={`Ir para página ${i + 1}`}
                  aria-current={i === currentPage ? "true" : undefined}
                />
              ))}
            </div>

            <button
              onClick={handleNext}
              disabled={currentPage === totalPages - 1 || isTransitioning}
              className="flex items-center justify-center w-10 h-10 rounded-full bg-[#21262d] border border-[#30363d] hover:bg-[#30363d] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              aria-label="Próxima página"
            >
              <ChevronRight className="w-5 h-5 text-[#e6edf3]" />
            </button>
          </div>
        )}
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


