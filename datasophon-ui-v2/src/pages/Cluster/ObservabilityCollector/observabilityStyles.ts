import { createStyles } from 'antd-style';

export const useObservabilityStyles = createStyles(({ css, token }) => ({
  workspace: css`
    min-height: 100%;
    padding: 20px;
    border-radius: 12px;
    background:
      radial-gradient(circle at 90% 0%, ${token.blue1} 0, transparent 28%),
      ${token.colorBgLayout};
  `,
  workspaceHeader: css`
    display: flex;
    justify-content: space-between;
    gap: 20px;
    align-items: center;
    margin-bottom: 16px;
  `,
  workspaceSubtitle: css`
    display: block;
    margin-top: 5px;
  `,
  workspaceControls: css`
    justify-content: flex-end;
  `,
  workspaceTabs: css`
    .ant-tabs-nav {
      margin-bottom: 14px;
      padding: 4px;
      border: 1px solid ${token.colorBorderSecondary};
      border-radius: 10px;
      background: ${token.colorBgContainer};
    }

    .ant-tabs-nav::before {
      display: none;
    }

    .ant-tabs-tab {
      margin: 0 !important;
      padding: 8px 20px !important;
      border-radius: 7px;
    }

    .ant-tabs-tab-active {
      background: ${token.blue1};
    }

    .ant-tabs-ink-bar {
      display: none;
    }
  `,
  panel: css`
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: 10px;
    overflow: hidden;
    box-shadow: ${token.boxShadowTertiary};
  `,
  filterBar: css`
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: flex-end;
    padding: 14px 18px;
    border-bottom: 1px solid ${token.colorBorderSecondary};
    background: ${token.colorFillAlter};

    .ant-form-item-label {
      padding-bottom: 4px;
    }
  `,
  quickBar: css`
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    padding: 10px 18px;
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
  overviewGrid: css`
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    padding: 14px 18px;
    border-bottom: 1px solid ${token.colorBorderSecondary};

    @media (max-width: 1100px) {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  `,
  overviewCard: css`
    border-color: ${token.colorBorderSecondary};
    box-shadow: none;

    .ant-statistic-content {
      font-size: 22px;
      line-height: 1.25;
    }
  `,
  statHint: css`
    min-height: 18px;
    margin-top: 5px;
    overflow: hidden;
    color: ${token.colorTextTertiary};
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  `,
  topologyWorkspace: css`
    display: grid;
    grid-template-columns: minmax(0, 1fr) 280px;
    min-height: 560px;

    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  `,
  topologyCanvas: css`
    min-width: 0;
    border-right: 1px solid ${token.colorBorderSecondary};
  `,
  insightPanel: css`
    padding: 18px;
    background: ${token.colorFillAlter};
  `,
  insightTitle: css`
    margin-bottom: 12px;
    color: ${token.colorText};
    font-weight: 600;
  `,
  insightItem: css`
    display: grid;
    grid-template-columns: 24px minmax(0, 1fr) auto;
    gap: 8px;
    align-items: center;
    padding: 10px 0;
    border-bottom: 1px solid ${token.colorBorderSecondary};
  `,
  insightRank: css`
    display: inline-flex;
    justify-content: center;
    align-items: center;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    background: ${token.blue1};
    color: ${token.colorPrimary};
    font-size: 12px;
    font-weight: 600;
  `,
  insightService: css`
    overflow: hidden;
    color: ${token.colorText};
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  `,
  tableWrap: css`
    .ant-pro-card-body {
      padding-inline: 18px;
    }

    .ant-table-thead > tr > th {
      background: ${token.colorFillAlter};
    }
  `,
  waterfallHeader: css`
    position: sticky;
    top: 0;
    z-index: 1;
    display: flex;
    align-items: center;
    padding: 9px 16px;
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
    padding: 7px 16px;
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
    padding: 16px;
  `,
  traceSummary: css`
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1px;
    margin-bottom: 16px;
    overflow: hidden;
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: 8px;
    background: ${token.colorBorderSecondary};

    > div {
      padding: 14px 16px;
      background: ${token.colorBgContainer};
    }
  `,
  drawerContext: css`
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: center;
    margin-bottom: 14px;
    padding: 10px 12px;
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: 8px;
    background: ${token.colorFillAlter};
  `,
  drawerContextMain: css`
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    min-width: 0;
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
