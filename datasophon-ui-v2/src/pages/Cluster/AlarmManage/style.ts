import { createStyles } from 'antd-style';

const useStyles = createStyles(({ token }) => ({
  titleRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
    marginBottom: 16,
  },
  title: {
    margin: '0 !important',
  },
  summaryRow: {
    marginBottom: 16,
  },
  summaryCard: {
    height: '100%',
    borderColor: token.colorBorderSecondary,
    boxShadow: token.boxShadowTertiary,
  },
  summaryContent: {
    display: 'flex',
    alignItems: 'center',
    gap: 14,
  },
  summaryIcon: {
    display: 'flex',
    width: 44,
    height: 44,
    flexShrink: 0,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: token.borderRadius,
    fontSize: 20,
  },
  guidance: {
    height: '100%',
  },
  tabsShell: {
    padding: '0 16px 16px',
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadiusLG,
    background: token.colorBgContainer,
    boxShadow: token.boxShadowTertiary,
  },
}));

export default useStyles;
