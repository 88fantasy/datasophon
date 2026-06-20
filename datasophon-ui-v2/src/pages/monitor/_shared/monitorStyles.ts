import { createStyles } from 'antd-style';

const useStyles = createStyles(({ token }) => ({
  dashboard: {
    padding: 24,
    [`@media screen and (max-width: ${token.screenMD}px)`]: {
      padding: 16,
    },
  },
  header: {
    marginBottom: 16,
  },
  title: {
    margin: '0 0 16px',
    color: token.colorTextHeading,
    fontWeight: 600,
  },
  meta: {
    display: 'block',
    margin: '-4px 0 16px',
    fontSize: 12,
    lineHeight: '20px',
  },
  metaSpin: {
    marginLeft: 8,
  },
  content: {
    // 行间距。响应式栅格已下放到 <PanelCol>（按 span 计算 xs/md/lg），
    // 不再用 .ant-col-* 内部类的媒体查询 hack。
    '.ant-row + .ant-row': {
      marginTop: 24,
    },
  },
  panelCard: {
    height: '100%',
    borderRadius: token.borderRadiusLG,
    boxShadow: token.boxShadowTertiary,
  },
  // 经 MonitorPanelCard 的 classNames.title 注入，替代 .ant-card-head-title 选择器。
  panelCardTitle: {
    color: token.colorTextHeading,
    overflow: 'hidden',
    whiteSpace: 'nowrap',
    textOverflow: 'ellipsis',
  },
  statTitle: {
    marginBottom: 8,
    color: token.colorTextSecondary,
    fontSize: token.fontSize,
    lineHeight: '22px',
  },
  statValue: {
    minHeight: 38,
    color: token.colorTextHeading,
    fontSize: 30,
    fontWeight: 600,
    lineHeight: '38px',
    whiteSpace: 'nowrap',
  },
  statSuffix: {
    marginLeft: 6,
    fontSize: 14,
    fontWeight: 500,
  },
  empty: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    margin: 0,
  },
  toolbar: {
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 8,
    marginBottom: 16,
    padding: '12px 16px',
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadiusLG,
    background: token.colorBgContainer,
    boxShadow: token.boxShadowTertiary,
  },
  toolbarSpacer: {
    flex: 1,
    minWidth: 12,
  },
  toolbarCountdown: {
    margin: 0,
    borderRadius: token.borderRadiusSM,
  },
  sectionHeader: {
    margin: '24px 0 12px',
    padding: '4px 12px',
    borderLeft: `4px solid ${token.colorPrimary}`,
  },
  tableDownRow: {
    '> td': {
      background: `${token.colorErrorBg} !important`,
    },
  },
}));

export default useStyles;
