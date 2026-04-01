import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';

// Mock dependencies - must be at top level
vi.mock('../../../utils/routerUtils', () => ({
  getRouteQuery: vi.fn(() => ({})),
  invokeGenPath: vi.fn((path) => path),
  replaceRouter: vi.fn(),
}));

vi.mock('antd', () => ({
  Spin: vi.fn(({ className }) => <div data-testid="spin" className={className}>Loading...</div>),
  Tabs: vi.fn(({ activeKey, items, onChange, tabBarExtraContent, destroyOnHidden, rootClassName, type }) => (
    <div data-testid="tabs" data-active={activeKey} data-destroy={String(destroyOnHidden)} data-type={type} className={rootClassName}>
      {tabBarExtraContent && <div data-testid="extra">{tabBarExtraContent}</div>}
      {items?.map((item: any) => (
        <button
          key={item.key}
          data-testid={`tab-${item.key}`}
          data-active={item.key === activeKey}
          onClick={() => onChange?.(item.key)}
        >
          {item.label}
        </button>
      ))}
      <div data-testid="content">
        {items?.find((item: any) => item.key === activeKey)?.children}
      </div>
    </div>
  )),
}));

// Import after mocks
import CommonTabs from './index';
import { getRouteQuery, replaceRouter } from '../../../utils/routerUtils';

describe('CommonTabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getRouteQuery).mockReturnValue({});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Basic rendering', () => {
    it('should render tabs with items', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} />);

      expect(screen.getByTestId('tabs')).toBeInTheDocument();
      expect(screen.getByTestId('tab-tab1')).toBeInTheDocument();
      expect(screen.getByTestId('tab-tab2')).toBeInTheDocument();
    });

    it('should use first item key as default active key', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.active).toBe('tab1');
    });

    it('should use defActiveKey when provided', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} defActiveKey="tab2" />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.active).toBe('tab2');
    });

    it('should use active key from URL query when available', () => {
      vi.mocked(getRouteQuery).mockImplementation((key) => {
        if (key === 'tab') return 'tab2';
        return {};
      });

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.active).toBe('tab2');
    });
  });

  describe('Tab switching', () => {
    it('should switch active tab on click', async () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        const tabs = screen.getByTestId('tabs');
        expect(tabs.dataset.active).toBe('tab2');
      });
    });

    it('should not switch tab when onBeforeChange returns false', async () => {
      const onBeforeChange = vi.fn().mockResolvedValue(false);

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} onBeforeChange={onBeforeChange} />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        expect(onBeforeChange).toHaveBeenCalledWith({ current: 'tab1', next: 'tab2' });
      });

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.active).toBe('tab1');
    });

    it('should switch tab when onBeforeChange returns true', async () => {
      const onBeforeChange = vi.fn().mockResolvedValue(true);

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} onBeforeChange={onBeforeChange} />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        const tabs = screen.getByTestId('tabs');
        expect(tabs.dataset.active).toBe('tab2');
      });
    });

    it('should switch tab when onBeforeChange is undefined', async () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        const tabs = screen.getByTestId('tabs');
        expect(tabs.dataset.active).toBe('tab2');
      });
    });

    it('should switch tab when onBeforeChange returns undefined', async () => {
      const onBeforeChange = vi.fn().mockResolvedValue(undefined);

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} onBeforeChange={onBeforeChange} />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        const tabs = screen.getByTestId('tabs');
        expect(tabs.dataset.active).toBe('tab2');
      });
    });
  });

  describe('URL binding', () => {
    it('should update URL when bindUrl is true and tab changes', async () => {
      vi.mocked(getRouteQuery).mockReturnValue({ existing: 'param' });

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} bindUrl={true} />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        expect(replaceRouter).toHaveBeenCalledWith({
          query: { existing: 'param', tab: 'tab2' }
        });
      });
    });

    it('should not update URL when bindUrl is false', async () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} bindUrl={false} />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        const tabs = screen.getByTestId('tabs');
        expect(tabs.dataset.active).toBe('tab2');
      });

      expect(replaceRouter).not.toHaveBeenCalled();
    });

    it('should use custom tabKey for URL binding', async () => {
      vi.mocked(getRouteQuery).mockReturnValue({});

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} bindUrl={true} tabKey="activeTab" />);

      fireEvent.click(screen.getByTestId('tab-tab2'));

      await waitFor(() => {
        expect(replaceRouter).toHaveBeenCalledWith({
          query: { activeTab: 'tab2' }
        });
      });
    });

    it('should read active key from custom tabKey in URL', () => {
      vi.mocked(getRouteQuery).mockImplementation((key) => {
        if (key === 'activeTab') return 'tab2';
        return {};
      });

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ];

      render(<CommonTabs memoTabItem={items} tabKey="activeTab" />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.active).toBe('tab2');
    });
  });

  describe('Async children (lazy loading)', () => {
    it('should render async children with Suspense', () => {
      const LazyComponent = React.lazy(() => Promise.resolve({ default: () => <div>Lazy Content</div> }));

      const items = [
        { key: 'tab1', label: 'Tab 1', asyncChildren: LazyComponent },
      ];

      render(<CommonTabs memoTabItem={items} />);

      expect(screen.getByTestId('spin')).toBeInTheDocument();
    });

    it('should pass props to async children component', async () => {
      const LazyComponent = React.lazy(() => Promise.resolve({
        default: ({ testProp }: { testProp: string }) => <div>{testProp}</div>
      }));

      const items = [
        { key: 'tab1', label: 'Tab 1', asyncChildren: LazyComponent, props: { testProp: 'test-value' } },
      ];

      render(<CommonTabs memoTabItem={items} />);

      await waitFor(() => {
        expect(screen.getByText('test-value')).toBeInTheDocument();
      });
    });

    it('should pass ref to async children component', async () => {
      const LazyComponent = React.lazy(() => Promise.resolve({
        default: React.forwardRef<HTMLDivElement>((_, ref) => <div ref={ref}>Lazy Content</div>)
      }));

      const items = [
        { key: 'tab1', label: 'Tab 1', asyncChildren: LazyComponent },
      ];

      render(<CommonTabs memoTabItem={items} />);

      await waitFor(() => {
        expect(screen.getByText('Lazy Content')).toBeInTheDocument();
      });
    });

    it('should prefer children over asyncChildren when both provided', () => {
      const LazyComponent = React.lazy(() => Promise.resolve({ default: () => <div>Lazy</div> }));

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Direct Children</div>, asyncChildren: LazyComponent },
      ];

      render(<CommonTabs memoTabItem={items} />);

      expect(screen.getByText('Direct Children')).toBeInTheDocument();
    });
  });

  describe('Tab configuration', () => {
    it('should filter out falsy items', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
        null,
        undefined,
        false,
        { key: 'tab2', label: 'Tab 2', children: <div>Content 2</div> },
      ] as any;

      render(<CommonTabs memoTabItem={items} />);

      expect(screen.getByTestId('tab-tab1')).toBeInTheDocument();
      expect(screen.getByTestId('tab-tab2')).toBeInTheDocument();
    });

    it('should set destroyOnHidden to true by default', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
      ];

      render(<CommonTabs memoTabItem={items} />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.destroy).toBe('true');
    });

    it('should allow custom destroyOnHidden value', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
      ];

      render(<CommonTabs memoTabItem={items} destroyOnHidden={false} />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.destroy).toBe('false');
    });

    it('should pass rootClassName to Tabs', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
      ];

      render(<CommonTabs memoTabItem={items} rootClassName="custom-class" />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.className).toContain('custom-class');
    });

    it('should pass type to Tabs', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
      ];

      render(<CommonTabs memoTabItem={items} type="card" />);

      const tabs = screen.getByTestId('tabs');
      expect(tabs.dataset.type).toBe('card');
    });

    it('should render tabBarExtraContent', () => {
      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
      ];

      render(<CommonTabs memoTabItem={items} tabBarExtraContent={<button>Extra</button>} />);

      expect(screen.getByTestId('extra')).toBeInTheDocument();
      expect(screen.getByText('Extra')).toBeInTheDocument();
    });
  });

  describe('Edge cases', () => {
    it('should handle empty memoTabItem array', () => {
      render(<CommonTabs memoTabItem={[]} />);

      expect(screen.getByTestId('tabs')).toBeInTheDocument();
    });

    it('should use undefined as active key when memoTabItem is empty and no defActiveKey', () => {
      vi.mocked(getRouteQuery).mockReturnValue('');

      render(<CommonTabs memoTabItem={[]} />);

      const tabs = screen.getByTestId('tabs');
      // When memoTabItem is empty, activeKey should be undefined
      expect(tabs.dataset.active).toBeUndefined();
    });

    it('should reset active key when current key not in items', async () => {
      const { rerender } = render(
        <CommonTabs memoTabItem={[{ key: 'tab1', label: 'Tab 1', children: <div>1</div> }]} defActiveKey="tab1" />
      );

      rerender(
        <CommonTabs memoTabItem={[{ key: 'tab2', label: 'Tab 2', children: <div>2</div> }]} defActiveKey="tab1" />
      );

      await waitFor(() => {
        const tabs = screen.getByTestId('tabs');
        expect(tabs.dataset.active).toBe('tab2');
      });
    });

    it('should use empty string as default tabKey', () => {
      vi.mocked(getRouteQuery).mockImplementation((key) => {
        if (key === 'tab') return 'tab1';
        return {};
      });

      const items = [
        { key: 'tab1', label: 'Tab 1', children: <div>Content 1</div> },
      ];

      render(<CommonTabs memoTabItem={items} />);

      expect(getRouteQuery).toHaveBeenCalledWith('tab');
    });
  });

  describe('Memo optimization', () => {
    it('should be wrapped with memo', () => {
      expect(CommonTabs.displayName).toBeUndefined();
      expect(typeof CommonTabs).toBe('object');
    });
  });
});
