import { createStyles } from 'antd-style';

export const useObservabilityStyles = createStyles(({ css, token }) => ({
  panel: css`
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: 8px;
    overflow: hidden;
  `,
  filterBar: css`
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: flex-end;
    padding: 16px 24px;
    border-bottom: 1px solid ${token.colorBorderSecondary};
  `,
  quickBar: css`
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    padding: 12px 24px;
    border-bottom: 1px solid ${token.colorBorderSecondary};
  `,
  toolbar: css`
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: center;
    padding: 12px 24px;
    border-bottom: 1px solid ${token.colorBorderSecondary};
  `,
  traceId: css`
    font-family: ${token.fontFamilyCode};
    font-size: 12px;
  `,
  spanName: css`
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: ${token.fontFamilyCode};
    font-size: 12px;
  `,
  serviceTag: css`
    color: ${token.purple};
    background: ${token.purple1};
    border-color: ${token.purple3};
  `,
  durationCell: css`
    display: flex;
    align-items: center;
    gap: 8px;
  `,
  durationBar: css`
    height: 6px;
    min-width: 4px;
    max-width: 120px;
    border-radius: 3px;
    background: ${token.blue3};
  `,
  waterfallHeader: css`
    position: sticky;
    top: 0;
    z-index: 1;
    display: flex;
    align-items: center;
    padding: 8px 24px;
    background: ${token.colorFillAlter};
    border-bottom: 1px solid ${token.colorBorderSecondary};
    color: ${token.colorTextSecondary};
    font-size: 12px;
    font-weight: 600;
  `,
  waterfallBody: css`
    max-height: 420px;
    overflow: auto;
  `,
  spanRow: css`
    display: flex;
    align-items: center;
    padding: 6px 24px;
    border-bottom: 1px solid ${token.colorBorderSecondary};
    cursor: pointer;

    &:hover {
      background: ${token.blue1};
    }
  `,
  selectedSpanRow: css`
    background: ${token.blue1};
  `,
  spanNameCol: css`
    display: flex;
    flex-shrink: 0;
    align-items: center;
    width: 340px;
    min-height: 24px;
  `,
  timelineCol: css`
    position: relative;
    flex: 1;
    height: 22px;
  `,
  spanBar: css`
    position: absolute;
    top: 50%;
    height: 12px;
    min-width: 3px;
    border-radius: 3px;
    background: ${token.blue3};
    transform: translateY(-50%);
  `,
  detailGrid: css`
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    max-height: 220px;
    overflow: auto;
    padding: 16px 24px;
  `,
  attrKey: css`
    color: ${token.colorTextTertiary};
    font-size: 11px;
  `,
  attrValue: css`
    word-break: break-all;
    font-family: ${token.fontFamilyCode};
    font-size: 12px;
  `,
  logDetail: css`
    overflow-x: auto;
    padding: 12px;
    border-radius: 6px;
    background: #1d1d1d;
    color: #d4d4d4;
    font-family: ${token.fontFamilyCode};
    font-size: 12px;
    line-height: 1.6;
  `,
}));
