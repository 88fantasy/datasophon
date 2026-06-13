import { OpenAIChatProvider, XRequest } from '@ant-design/x-sdk';
import { request } from '@umijs/max';

import type { ConversationItem } from './data';

export const CHAT_API_URL = '/ddh/api/v2/chat/completions';

function getCsrfToken(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : '';
}

export const createChatProvider = (
  conversationId?: number,
  clusterId?: number,
  model?: string,
) =>
  new OpenAIChatProvider({
    request: XRequest(CHAT_API_URL, {
      manual: true,
      credentials: 'include' as RequestCredentials,
      // Read CSRF token at request time to avoid stale-token issues.
      // XRequest's middlewares.onRequest receives (url, requestInit) at runtime;
      // the SDK's TS types are overly strict, so we cast to any here.
      middlewares: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        onRequest: (url: string, init: any) => {
          const token = getCsrfToken();
          return [
            url,
            {
              ...init,
              headers: {
                ...init.headers,
                ...(token ? { 'X-XSRF-TOKEN': token } : {}),
              },
            },
          ];
        },
      } as any,
      params: {
        model: model ?? 'claude-sonnet-4-6',
        stream: true,
        ...(conversationId ? { conversationId } : {}),
        ...(clusterId ? { clusterId } : {}),
      },
    }),
  });

export async function fetchChatConfig(): Promise<{ model: string }> {
  const res = await request<{ data: { model: string } }>('/chat/config', {
    method: 'GET',
  });
  return res.data ?? { model: 'claude-sonnet-4-6' };
}

export async function fetchConversations(): Promise<ConversationItem[]> {
  const res = await request<{
    data: Array<{ id: number; title: string; updateTime: string }>;
  }>('/chat/conversations', { method: 'GET' });
  return (res.data ?? []).map((c) => ({
    key: String(c.id),
    label: c.title,
    group: formatGroup(c.updateTime),
  }));
}

export async function fetchMessages(conversationId: number) {
  const res = await request<{
    data: Array<{
      id: number;
      role: string;
      content: string;
      createTime: string;
    }>;
  }>(`/chat/conversations/${conversationId}/messages`, {
    method: 'GET',
  });
  return res.data ?? [];
}

export async function deleteConversation(conversationId: number) {
  await request(`/chat/conversations/${conversationId}`, {
    method: 'DELETE',
  });
}

function formatGroup(isoStr: string): string {
  const date = new Date(isoStr);
  const now = new Date();
  const diffDays = Math.floor((now.getTime() - date.getTime()) / 86400000);
  if (diffDays === 0) return '今天';
  if (diffDays === 1) return '昨天';
  if (diffDays <= 7) return '本周';
  return '更早';
}
