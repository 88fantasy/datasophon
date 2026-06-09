import Anthropic from "@anthropic-ai/sdk";

const DATASOPHON_API_URL =
  process.env.DATASOPHON_API_URL ?? "http://localhost:8080/ddh/api";
const AGENT_INTERNAL_TOKEN =
  process.env.AGENT_INTERNAL_TOKEN ?? "change-me";

async function internalGet(path: string): Promise<unknown> {
  const res = await fetch(`${DATASOPHON_API_URL}${path}`, {
    headers: { "X-Agent-Token": AGENT_INTERNAL_TOKEN },
  });
  if (!res.ok) throw new Error(`internal API ${path} returned ${res.status}`);
  return res.json();
}

export const TOOLS: Anthropic.Tool[] = [
  {
    name: "list_clusters",
    description: "列出所有集群信息，包括集群 ID、名称、状态",
    input_schema: {
      type: "object" as const,
      properties: {},
      required: [],
    },
  },
  {
    name: "list_hosts",
    description: "列出指定集群下的所有主机，包括主机名、IP、状态",
    input_schema: {
      type: "object" as const,
      properties: {
        cluster_id: {
          type: "number",
          description: "集群 ID（来自 list_clusters 的返回值）",
        },
      },
      required: ["cluster_id"],
    },
  },
  {
    name: "list_services",
    description: "列出指定集群下所有服务实例，包括服务名、版本、运行状态",
    input_schema: {
      type: "object" as const,
      properties: {
        cluster_id: {
          type: "number",
          description: "集群 ID",
        },
      },
      required: ["cluster_id"],
    },
  },
];

export async function runTool(
  name: string,
  input: Record<string, unknown>
): Promise<string> {
  switch (name) {
    case "list_clusters": {
      const data = await internalGet("/internal/agent/clusters");
      return JSON.stringify(data, null, 2);
    }
    case "list_hosts": {
      const clusterId = input.cluster_id as number;
      const data = await internalGet(
        `/internal/agent/clusters/${clusterId}/hosts`
      );
      return JSON.stringify(data, null, 2);
    }
    case "list_services": {
      const clusterId = input.cluster_id as number;
      const data = await internalGet(
        `/internal/agent/clusters/${clusterId}/services`
      );
      return JSON.stringify(data, null, 2);
    }
    default:
      return `unknown tool: ${name}`;
  }
}
