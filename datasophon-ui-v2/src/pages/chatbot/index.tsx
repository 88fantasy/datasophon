import { UserOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Bubble, Conversations, Sender, Think, XProvider } from '@ant-design/x';
import type {
  BubbleItemType,
  BubbleListProps,
} from '@ant-design/x/es/bubble/interface';
import XMarkdown from '@ant-design/x-markdown';
import { useXChat } from '@ant-design/x-sdk';
import { Avatar, Card } from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import type { ConversationItem, ParsedMessage } from './data';
import {
  createChatProvider,
  deleteConversation,
  fetchChatConfig,
  fetchConversations,
} from './service';
import { useStyles } from './style';

const WELCOME_TEXT = '🤖 你好，有什么可以帮你？';

const TypewriterTitle: React.FC = () => {
  const { styles } = useStyles();
  const [index, setIndex] = useState(0);
  const done = index >= WELCOME_TEXT.length;

  useEffect(() => {
    const timer = setInterval(() => {
      setIndex((i) => {
        if (i >= WELCOME_TEXT.length) {
          clearInterval(timer);
          return i;
        }
        return i + 1;
      });
    }, 80);
    return () => clearInterval(timer);
  }, []);

  return (
    <>
      {WELCOME_TEXT.slice(0, index)}
      {!done && <span className={styles.cursor}>|</span>}
    </>
  );
};

const parser = (message: { content: string; role: string }): ParsedMessage => {
  const { content, role } = message;
  if (role !== 'assistant') return { role: 'user', content };

  const trimmed = content.trimStart();

  const fullMatch = trimmed.match(/^<think>([\s\S]*?)<\/think>([\s\S]*)$/);
  if (fullMatch) {
    return {
      role: 'assistant',
      thinkContent: fullMatch[1],
      content: fullMatch[2].trimStart(),
    };
  }

  const partialMatch = trimmed.match(/^<think>([\s\S]*)$/);
  if (partialMatch) {
    return { role: 'assistant', thinkContent: partialMatch[1], content: '' };
  }

  return { role: 'assistant', content };
};

const STREAMING_ACTIVE = { hasNextChunk: true, enableAnimation: true };
const STREAMING_IDLE = { hasNextChunk: false, enableAnimation: true };

const roleConfig: BubbleListProps['role'] = {
  user: {
    placement: 'end',
    avatar: <Avatar icon={<UserOutlined />} />,
  },
  ai: {
    placement: 'start',
    avatar: (
      <Avatar
        style={{
          background: 'transparent',
          fontSize: 22,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        🤖
      </Avatar>
    ),
    typing: { effect: 'typing', step: 2, interval: 20 },
    contentRender: (
      content: string,
      info: { status?: string; loading?: boolean },
    ) => {
      if (info?.loading || !content) return undefined;
      return (
        <XMarkdown
          streaming={
            info?.status === 'updating' ? STREAMING_ACTIVE : STREAMING_IDLE
          }
        >
          {content}
        </XMarkdown>
      );
    },
  },
};

const ChatbotPage: React.FC = () => {
  const { styles } = useStyles();
  const idCounter = useRef(0);
  const generateId = useCallback(() => `conv-${++idCounter.current}`, []);

  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [activeKey, setActiveKey] = useState<string>('');
  const [inputValue, setInputValue] = useState('');
  const [activeConvId, setActiveConvId] = useState<number | undefined>();
  const [chatModel, setChatModel] = useState<string>('qwen3.7-plus');

  useEffect(() => {
    fetchChatConfig()
      .then((cfg) => setChatModel(cfg.model))
      .catch(() => {});
  }, []);

  useEffect(() => {
    fetchConversations()
      .then((convs) => {
        if (convs.length > 0) {
          setConversations(convs);
          setActiveKey(convs[0].key);
          setActiveConvId(Number(convs[0].key));
        } else {
          newChat();
        }
      })
      .catch(() => {
        newChat();
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const provider = useMemo(
    () => createChatProvider(activeConvId, undefined, chatModel) as any,
    [activeConvId, chatModel],
  );
  const { onRequest, abort, isRequesting, parsedMessages } = useXChat<
    any,
    ParsedMessage
  >({
    provider,
    conversationKey: activeKey,
    parser,
    requestPlaceholder: { role: 'assistant', content: '' },
  });

  const handleActiveChange = (key: string) => {
    setActiveKey(key);
    const convId = Number(key);
    if (!Number.isNaN(convId)) {
      setActiveConvId(convId);
    }
  };

  const sendMessage = (content: string) => {
    setInputValue('');
    setConversations((prev) =>
      prev.map((c) =>
        c.key === activeKey && c.isDraft
          ? { ...c, label: content.slice(0, 20), isDraft: false }
          : c,
      ),
    );
    onRequest({ messages: [{ role: 'user', content }] });
    setTimeout(() => {
      fetchConversations()
        .then(setConversations)
        .catch(() => {});
    }, 2000);
  };

  const newChat = () => {
    const key = generateId();
    setConversations((prev) => [
      { key, label: '新对话', group: '今天', isDraft: true },
      ...prev,
    ]);
    setActiveKey(key);
  };

  const bubbleItems = useMemo<BubbleItemType[]>(
    () =>
      parsedMessages.map((msg) => {
        const parsed = msg.message as ParsedMessage;
        const isAI = parsed.role === 'assistant';
        const thinkContent =
          parsed.role === 'assistant' ? parsed.thinkContent : undefined;

        const item: BubbleItemType = {
          key: msg.id,
          role: isAI ? 'ai' : 'user',
          content: parsed.content,
          loading: isAI && msg.status === 'loading',
          status: msg.status,
        };

        if (isAI && thinkContent) {
          item.header = <Think>{thinkContent}</Think>;
        }

        return item;
      }),
    [parsedMessages],
  );

  const hasMessages = parsedMessages.length > 0;

  return (
    <PageContainer
      ghost
      childrenContentStyle={{
        paddingBlock: 0,
        height: 'calc(100vh - 160px)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <Card
        variant="borderless"
        style={{
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
        styles={{
          body: {
            flex: 1,
            padding: 0,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          },
        }}
      >
        <XProvider>
          <div className={styles.layout}>
            <div className={styles.sidebar}>
              <Conversations
                items={conversations}
                activeKey={activeKey}
                onActiveChange={handleActiveChange}
                groupable
                menu={(conversation) => ({
                  items: [{ key: 'delete', label: '删除', danger: true }],
                  onClick: ({ key }) => {
                    if (key === 'delete') {
                      const convId = Number(conversation.key);
                      if (!Number.isNaN(convId)) {
                        deleteConversation(convId).catch(console.error);
                      }
                      setConversations((prev) => {
                        const next = prev.filter(
                          (c) => c.key !== conversation.key,
                        );
                        if (next.length === 0) {
                          newChat();
                        } else if (activeKey === conversation.key) {
                          setActiveKey(next[0]?.key ?? '');
                          const nextConvId = Number(next[0]?.key);
                          if (!Number.isNaN(nextConvId)) {
                            setActiveConvId(nextConvId);
                          }
                        }
                        return next;
                      });
                    }
                  },
                })}
                creation={{ onClick: newChat, label: '新建对话' }}
              />
            </div>

            <div className={styles.main}>
              {hasMessages && (
                <div className={styles.messages}>
                  <Bubble.List
                    items={bubbleItems}
                    role={roleConfig}
                    autoScroll
                    styles={{ root: { maxWidth: 940 } }}
                  />
                </div>
              )}

              <div
                className={hasMessages ? styles.footer : styles.footerCenter}
              >
                {!hasMessages && (
                  <div className={styles.welcomeTitle}>
                    <TypewriterTitle />
                  </div>
                )}
                <Sender
                  value={inputValue}
                  onChange={setInputValue}
                  loading={isRequesting}
                  onSubmit={sendMessage}
                  onCancel={abort}
                  placeholder="输入消息，按 Enter 发送..."
                  autoSize={{ minRows: 4, maxRows: 8 }}
                  style={{ maxWidth: 940, width: '100%' }}
                  styles={{ input: { paddingBlock: 0 } }}
                />
              </div>
            </div>
          </div>
        </XProvider>
      </Card>
    </PageContainer>
  );
};

export default ChatbotPage;
