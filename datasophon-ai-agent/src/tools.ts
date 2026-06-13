import { tool, createSdkMcpServer } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";

// Base URL for internal agent endpoints — no /api prefix.
// AgentToolController is at /ddh/internal/agent/**, not /ddh/api/internal/agent/**.
const DATASOPHON_API_URL =
  process.env.DATASOPHON_API_URL ?? "http://localhost:8080/ddh";
const AGENT_INTERNAL_TOKEN =
  process.env.AGENT_INTERNAL_TOKEN ?? "change-me";

async function internalGet(path: string): Promise<unknown> {
  const res = await fetch(`${DATASOPHON_API_URL}${path}`, {
    headers: { "X-Agent-Token": AGENT_INTERNAL_TOKEN },
  });
  if (!res.ok) throw new Error(`internal API ${path} returned ${res.status}`);
  return res.json();
}

const listClusters = tool(
  "list_clusters",
  "列出所有集群信息，包括集群 ID、名称、状态",
  {},
  async () => {
    try {
      const data = await internalGet("/internal/agent/clusters");
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    } catch (e) {
      return {
        content: [{ type: "text", text: `查询集群失败：${String(e)}` }],
        isError: true,
      };
    }
  },
  { annotations: { readOnlyHint: true } }
);

const listHosts = tool(
  "list_hosts",
  "列出指定集群下的所有主机，包括主机名、IP、状态",
  { cluster_id: z.number().describe("集群 ID（来自 list_clusters 的返回值）") },
  async (args) => {
    try {
      const data = await internalGet(
        `/internal/agent/clusters/${args.cluster_id}/hosts`
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    } catch (e) {
      return {
        content: [{ type: "text", text: `查询主机失败：${String(e)}` }],
        isError: true,
      };
    }
  },
  { annotations: { readOnlyHint: true } }
);

const listServices = tool(
  "list_services",
  "列出指定集群下所有服务实例，包括服务名、版本、运行状态",
  { cluster_id: z.number().describe("集群 ID") },
  async (args) => {
    try {
      const data = await internalGet(
        `/internal/agent/clusters/${args.cluster_id}/services`
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    } catch (e) {
      return {
        content: [{ type: "text", text: `查询服务失败：${String(e)}` }],
        isError: true,
      };
    }
  },
  { annotations: { readOnlyHint: true } }
);

// In-process MCP server — registered as "datasophon", so tool names become
// mcp__datasophon__list_clusters / list_hosts / list_services.
export const datasophonMcpServer = createSdkMcpServer({
  name: "datasophon",
  version: "1.0.0",
  tools: [listClusters, listHosts, listServices],
});
