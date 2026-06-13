// src/pages/chatbot/style.ts
import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => ({
  layout: css`
    display: flex;
    flex: 1;
    overflow: hidden;
  `,

  sidebar: css`
    width: 260px;
    background: ${token.colorBgContainer};
    border-right: 1px solid ${token.colorBorderSecondary};
    display: flex;
    flex-direction: column;
    overflow: hidden;
  `,

  main: css`
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    min-width: 0;
    background: ${token.colorBgContainer};
  `,

  messages: css`
    flex: 1;
    overflow-y: auto;
    padding: ${token.paddingMD}px;
    display: flex;
    flex-direction: column;
    align-items: center;

    > * {
      width: 100%;
    }
  `,

  footer: css`
    padding: ${token.paddingMD}px;
    border-top: 1px solid ${token.colorBorderSecondary};
    display: flex;
    justify-content: center;
  `,

  footerCenter: css`
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: ${token.paddingLG}px;
    gap: 32px;
    margin-top: -10%;
  `,

  welcomeTitle: css`
    font-size: 32px;
    font-weight: 600;
    color: ${token.colorText};
    text-align: center;
  `,

  cursor: css`
    animation: chatbot-blink 0.8s step-end infinite;

    @keyframes chatbot-blink {
      0%, 100% { opacity: 1; }
      50% { opacity: 0; }
    }
  `,

  toolCallsPanel: css`
    margin-bottom: 4px;
  `,

  toolCallHeader: css`
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
  `,

  toolCallName: css`
    font-weight: 500;
  `,

  toolCallSuccess: css`
    color: ${token.colorSuccess};
  `,

  toolCallError: css`
    color: ${token.colorError};
  `,

  toolCallDuration: css`
    color: ${token.colorTextTertiary};
    font-size: 12px;
  `,

  toolCallBody: css`
    display: flex;
    flex-direction: column;
    gap: 4px;
  `,

  toolCallLabel: css`
    font-size: 12px;
  `,

  toolCallPre: css`
    margin: 0;
    padding: 8px;
    background: ${token.colorFillTertiary};
    border-radius: ${token.borderRadiusSM}px;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 200px;
    overflow-y: auto;
  `,
}));
