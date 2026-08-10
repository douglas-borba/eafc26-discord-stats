import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/supabase/auth-server";
export async function POST() { const auth = await requireAdmin(); if (auth.kind !== "allowed" && auth.kind !== "forbidden") return NextResponse.json({ error: "unauthorized" }, { status: 401 }); await auth.client.auth.signOut(); return NextResponse.json({ status: "ok" }); }
