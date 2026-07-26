import { createStyles } from 'antd-style';

const useStyles = createStyles(({ token }) => ({
  pageLayout: {
    minHeight: 'calc(100vh - 60px)',
    background: token.colorBgLayout,
  },
  sider: {
    borderRight: '1px solid rgba(255, 255, 255, 0.08)',
    background: '#0f1f3d',
  },
  siderBody: {
    display: 'flex',
    height: '100%',
    minHeight: 'calc(100vh - 60px)',
    flexDirection: 'column',
    background:
      'linear-gradient(180deg, #10264b 0%, #0f1f3d 48%, #0a1730 100%)',
  },
  siderHeader: {
    padding: '18px 16px 16px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
  },
  siderEyebrow: {
    display: 'block',
    marginBottom: 6,
    color: 'rgba(255, 255, 255, 0.56)',
    fontSize: 12,
    letterSpacing: 1,
  },
  siderClusterName: {
    display: 'block',
    overflow: 'hidden',
    color: '#fff',
    fontSize: 16,
    fontWeight: 600,
    lineHeight: '24px',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  menu: {
    flex: 1,
    padding: '10px 8px 16px',
    overflowY: 'auto',
    borderInlineEnd: 'none !important',
    background: 'transparent',
    '&.ant-menu-dark .ant-menu-item-selected': {
      background: '#2563eb',
      boxShadow: '0 6px 16px -8px rgba(37, 99, 235, 0.75)',
    },
    '&.ant-menu-dark .ant-menu-submenu-selected > .ant-menu-submenu-title': {
      color: '#ffffff',
    },
  },
  content: {
    minWidth: 0,
    padding: 20,
    background: token.colorBgLayout,
    '@media (max-width: 900px)': {
      padding: 12,
    },
  },
  contentInner: {
    minWidth: 0,
  },
  clusterBar: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
    marginBottom: 16,
    padding: '12px 16px',
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadiusLG,
    background: token.colorBgContainer,
    boxShadow: token.boxShadowTertiary,
    '@media (max-width: 900px)': {
      alignItems: 'flex-start',
      flexDirection: 'column',
    },
  },
  clusterIdentity: {
    minWidth: 0,
  },
  breadcrumb: {
    display: 'block',
    marginBottom: 3,
    color: token.colorTextTertiary,
    fontSize: 12,
  },
  clusterNameRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    minWidth: 0,
  },
  clusterName: {
    overflow: 'hidden',
    color: token.colorTextHeading,
    fontSize: 16,
    fontWeight: 600,
    lineHeight: '24px',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  clusterActions: {
    display: 'flex',
    flexShrink: 0,
    gap: 8,
    '@media (max-width: 900px)': {
      width: '100%',
    },
  },
  outlet: {
    minWidth: 0,
    minHeight: 0,
  },
}));

export default useStyles;
