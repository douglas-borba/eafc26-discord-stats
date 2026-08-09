/**
 * Client for fetching AI-generated panorama from the Spring Boot backend.
 * Uses the REST endpoint that validates contextKey instead of direct database access.
 */

interface PanoramaResponse {
  status: "success" | "unavailable" | "no_matches";
  text: string | null;
}

/**
 * Fetches the AI panorama from the backend REST API.
 * The backend validates that the panorama belongs to the current contextKey.
 *
 * @returns The panorama text if available, null otherwise
 */
export async function fetchAiPanorama(clubId: string): Promise<string | null> {
  try {
    const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
    const response = await fetch(`${backendUrl}/api/panorama?clubId=${encodeURIComponent(clubId)}`, {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      cache: "no-store", // Always get fresh data
    });

    if (!response.ok) {
      console.error(`Failed to fetch panorama: ${response.status} ${response.statusText}`);
      return null;
    }

    const data: PanoramaResponse = await response.json();

    if (data.status === "success" && data.text) {
      return data.text;
    }

    // Status is unavailable or no_matches
    return null;
  } catch (error) {
    console.error("Error fetching AI panorama from backend:", error);
    return null;
  }
}
