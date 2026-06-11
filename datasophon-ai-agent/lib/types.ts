/**
 * Shared message type used across route handlers and agent.
 * Compatible with both Anthropic and OpenAI message formats.
 */
export type ChatMessage = {
  role: "user" | "assistant" | "system";
  content: string | Array<{ type: string; text?: string }>;
};
