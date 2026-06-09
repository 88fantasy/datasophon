import { OpenAIChatProvider, XRequest } from '@ant-design/x-sdk';
import { request } from '@umijs/max';

import type { ConversationItem } from './data';

export const CHAT_API_URL = '/ddh/api/v2/chat/completions';

function getCsrfHeaders(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  const token = match ? decodeURIComponent(match[1]) : '';
  return token ? { 'X-XSRF-TOKEN': token } : {};
}

export const createChatProvider = (
  conversationId?: number,
  clusterId?: number,
) =>
  new OpenAIChatProvider({
    request: XRequest(CHAT_API_URL, {
      manual: true,
      credentials: 'include' as RequestCredentials,
      headers: {
        ...getCsrfHeaders(),
      },
      params: {
        model: 'claude-opus-4-8',
        stream: true,
        ...(conversationId ? { conversationId } : {}),
        ...(clusterId ? { clusterId } : {}),
      },
    }),
  });

export async function fetchConversations(): Promise<ConversationItem[]> {
  const res = await request<{
    data: Array<{ id: number; title: string; updateTime: string }>;
  }>('/ddh/api/v2/chat/conversations', { method: 'GET' });
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
  }>(`/ddh/api/v2/chat/conversations/${conversationId}/messages`, {
    method: 'GET',
  });
  return res.data ?? [];
}

export async function deleteConversation(conversationId: number) {
  await request(`/ddh/api/v2/chat/conversations/${conversationId}`, {
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
