import { testConnectivity } from "@/lib/agent";

export const runtime = "nodejs";

export async function GET() {
  const result = await testConnectivity();
  return Response.json(result, { status: result.ok ? 200 : 502 });
}
