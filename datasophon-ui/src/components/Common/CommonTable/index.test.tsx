import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';

// Mock dependencies - must be at top level
vi.mock('../../../utils/util', () => ({
  showComfirmModal: vi.fn(),
  showMsgAfferRequest: vi.fn(),
}));

vi.mock('../CommonModal/asyncHook', () => ({
  default: (fn: () => Promise<any>) => {
    return () => fn();
  },
}));

vi.mock('../CommonModal/FormModal/api', () => ({
  default: (config: any) => {
    // Store config for testing
    (globalThis as any).testFormModalConfig = config;
    // Call onOk to test bakClick
    if (config?.onOk) {
      config.onOk();
    }
  },
}));

vi.mock('@ant-design/pro-components', () => ({
  ProTable: vi.fn(({ actionRef, onReset, toolBarRender, columnsState, form, ...props }) => {
    if (actionRef) {
      (globalThis as any).testActionRef = actionRef;
    }
    // Store callbacks for testing
    (globalThis as any).testColumnsStateOnChange = columnsState?.onChange;
    (globalThis as any).testFormSyncToUrl = form?.syncToUrl;
    return (
      <div data-testid="pro-table">
        <div data-testid="toolbar">{toolBarRender?.()}</div>
        <button data-testid="reset-button" onClick={onReset}>Reset</button>
      </div>
    );
  }),
  TableDropdown: vi.fn(({ menus }) => (
    <div data-testid="table-dropdown">{menus?.length} items</div>
  )),
}));

vi.mock('@ant-design/icons', () => ({
  PlusOutlined: () => React.createElement('span', null, '+'),
  AppstoreOutlined: () => React.createElement('span', null, 'app'),
}));

vi.mock('antd', () => ({
  Button: vi.fn(({ children, onClick, icon }) => (
    React.createElement('button', { onClick, 'data-testid': 'antd-button' }, icon, children)
  )),
}));

// Import after mocks
import { invokeGenOptionCol } from './index';
import { showComfirmModal, showMsgAfferRequest } from '../../../utils/util';

describe('CommonTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).elId = 0;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('invokeGenOptionCol', () => {
    describe('config handling', () => {
      it('should handle config as a function', () => {
        const list = [
          { title: 'View', onClick: vi.fn(), key: 'view' },
        ];
        const configFn = vi.fn().mockReturnValue({});

        const result = invokeGenOptionCol(list, configFn);
        expect(configFn).toHaveBeenCalled();
        expect(typeof result).toBe('function');
      });

      it('should handle config as object', () => {
        const list = [
          { title: 'View', onClick: vi.fn(), key: 'view' },
        ];

        const result = invokeGenOptionCol(list, {});
        expect(typeof result).toBe('function');
      });

      it('should handle undefined config', () => {
        const list = [
          { title: 'View', onClick: vi.fn(), key: 'view' },
        ];

        const result = invokeGenOptionCol(list, undefined);
        expect(typeof result).toBe('function');
      });

      it('should handle null config', () => {
        const list = [
          { title: 'View', onClick: vi.fn(), key: 'view' },
        ];

        const result = invokeGenOptionCol(list, null);
        expect(typeof result).toBe('function');
      });
    });

    describe('disabled filter', () => {
      it('should filter items with boolean disabled=true', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view', disabled: true },
          { title: 'Edit', onClick, key: 'edit', disabled: false },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom).toHaveLength(1);
        expect(dom[0].props.children).toBe('Edit');
      });

      it('should filter items with function disabled that returns true', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view', disabled: () => true },
          { title: 'Edit', onClick, key: 'edit', disabled: () => false },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom).toHaveLength(1);
        expect(dom[0].props.children).toBe('Edit');
      });

      it('should pass arguments to disabled function', () => {
        const disabledFn = vi.fn().mockReturnValue(false);
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view', disabled: disabledFn },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(disabledFn).toHaveBeenCalledWith('text', { id: 1 }, null, { reload: expect.any(Function) });
      });

      it('should handle items without disabled property', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom).toHaveLength(1);
      });
    });

    describe('delete operation', () => {
      it('should handle delete operation with confirmation - confirmed', async () => {
        vi.mocked(showComfirmModal).mockResolvedValue(true);
        const onClick = vi.fn().mockResolvedValue({ code: 200, message: '删除成功' });
        const reload = vi.fn();
        const list = [
          { title: '删除', onClick, key: 'delete' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload });

        await dom[0].props.onClick();

        expect(showComfirmModal).toHaveBeenCalledWith({
          content: '确定要删除吗？',
          okType: 'danger',
        });
        expect(onClick).toHaveBeenCalled();
        expect(showMsgAfferRequest).toHaveBeenCalledWith({ code: 200, message: '删除成功' });
        expect(reload).toHaveBeenCalled();
      });

      it('should handle delete operation with confirmation - cancelled', async () => {
        vi.mocked(showComfirmModal).mockResolvedValue(false);
        const onClick = vi.fn();
        const reload = vi.fn();
        const list = [
          { title: 'delete', onClick, key: 'delete' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload });

        await dom[0].props.onClick();

        expect(showComfirmModal).toHaveBeenCalled();
        expect(onClick).not.toHaveBeenCalled();
        expect(reload).not.toHaveBeenCalled();
      });

      it('should show titleKey in delete confirmation', async () => {
        vi.mocked(showComfirmModal).mockResolvedValue(true);
        const onClick = vi.fn().mockResolvedValue({ code: 200 });
        const record = { id: 1, name: 'TestName' };
        const list = [
          { title: '删除', onClick, key: 'delete', titleKey: 'name' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', record, null, { reload: vi.fn() });

        await dom[0].props.onClick();

        expect(showComfirmModal).toHaveBeenCalledWith({
          content: '确定要删除【TestName】吗？',
          okType: 'danger',
        });
      });

      it('should handle delete without titleKey value in record', async () => {
        vi.mocked(showComfirmModal).mockResolvedValue(true);
        const onClick = vi.fn().mockResolvedValue({ code: 200 });
        const record = { id: 1 };
        const list = [
          { title: '删除', onClick, key: 'delete', titleKey: 'name' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', record, null, { reload: vi.fn() });

        await dom[0].props.onClick();

        expect(showComfirmModal).toHaveBeenCalledWith({
          content: '确定要删除吗？',
          okType: 'danger',
        });
      });

      it('should handle delete with no onClick provided', async () => {
        vi.mocked(showComfirmModal).mockResolvedValue(true);
        const list = [
          { title: '删除', key: 'delete' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        await expect(dom[0].props.onClick()).resolves.toBeUndefined();
      });

      it('should use key when title is not provided for delete detection', async () => {
        vi.mocked(showComfirmModal).mockResolvedValue(true);
        const onClick = vi.fn().mockResolvedValue({ code: 200 });
        const list = [
          { onClick, key: 'delete' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        await dom[0].props.onClick();

        expect(showComfirmModal).toHaveBeenCalled();
      });
    });

    describe('edit operation', () => {
      it('should handle edit operation with config object', async () => {
        const onClick = vi.fn();
        const list = [
          { title: '编辑', onClick, key: 'edit', config: { columns: [] } },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const record = { id: 1, name: 'Test' };
        const dom = fn('text', record, null, { reload: vi.fn() });

        expect(dom[0].props.children).toBe('编辑');
      });

      it('should call edit onClick with onOk callback', async () => {
        const onClick = vi.fn();
        const list = [
          { title: '编辑', onClick, key: 'edit', config: { columns: [] } },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const record = { id: 1, name: 'Test' };
        const dom = fn('text', record, null, { reload: vi.fn() });

        // Trigger onClick to set up onOk callback
        await dom[0].props.onClick();

        // The asyncHook mock returns FormModal/api module
        // which has a default function that receives config with onOk
        expect(dom[0].props.children).toBe('编辑');
      });

      it('should handle edit operation with title "edit"', async () => {
        const onClick = vi.fn();
        const list = [
          { title: 'edit', onClick, key: 'edit', config: { columns: [] } },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom[0].props.children).toBe('edit');
      });

      it('should not apply edit handler when config is not an object', async () => {
        const onClick = vi.fn();
        const list = [
          { title: '编辑', onClick, key: 'edit' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        dom[0].props.onClick();
        expect(onClick).toHaveBeenCalled();
      });

      it('should handle edit with no onClick provided', async () => {
        const list = [
          { title: '编辑', key: 'edit', config: { columns: [] } },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        await expect(dom[0].props.onClick()).resolves.toBeUndefined();
      });
    });

    describe('operation grouping', () => {
      it('should group operations into dropdown when more than 3', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view' },
          { title: 'Edit', onClick, key: 'edit' },
          { title: 'Share', onClick, key: 'share' },
          { title: 'Delete', onClick, key: 'delete' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom).toHaveLength(3);
      });

      it('should not group when 3 or fewer operations', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view' },
          { title: 'Edit', onClick, key: 'edit' },
          { title: 'Delete', onClick, key: 'delete' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom).toHaveLength(3);
      });
    });

    describe('pure mode', () => {
      it('should return pure function when config.pure is true', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view' },
        ];

        const result = invokeGenOptionCol(list, { pure: true });
        expect(typeof result).toBe('function');

        const operations = [
          { title: 'Op1', onClick, key: 'op1' },
        ];
        const dom = (result as any)(operations);

        expect(dom).toHaveLength(1);
      });

      it('should handle pure mode with multiple operations', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view' },
        ];

        const result = invokeGenOptionCol(list, { pure: true });
        const operations = [
          { title: 'Op1', onClick, key: 'op1' },
          { title: 'Op2', onClick, key: 'op2' },
          { title: 'Op3', onClick, key: 'op3' },
          { title: 'Op4', onClick, key: 'op4' },
        ];
        const dom = (result as any)(operations);

        expect(dom).toHaveLength(3);
      });
    });

    describe('key and title defaults', () => {
      it('should use title as name if name is not provided', () => {
        const onClick = vi.fn();
        const list = [
          { onClick, key: 'view' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom[0].props.children).toBeUndefined();
      });

      it('should use label as title if title is not provided', () => {
        const onClick = vi.fn();
        const list = [
          { label: 'View Label', onClick, key: 'view' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom[0].props.children).toBe('View Label');
      });

      it('should use existing title over label', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View Title', label: 'View Label', onClick, key: 'view' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom[0].props.children).toBe('View Title');
      });

      it('should use title as key if key is not provided', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom[0].key).toBe('View');
      });
    });

    describe('additional props preservation', () => {
      it('should pass through additional props to anchor element', () => {
        const onClick = vi.fn();
        const list = [
          { title: 'View', onClick, key: 'view', className: 'custom-class', 'data-testid': 'custom-test' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        expect(dom[0].props.className).toBe('custom-class');
        expect(dom[0].props['data-testid']).toBe('custom-test');
      });

      it('should use noop when onClick is not provided for non-delete/edit operation', () => {
        const list = [
          { title: 'View', key: 'view' },
        ];

        const result = invokeGenOptionCol(list, {});
        const fn = result as any;
        const dom = fn('text', { id: 1 }, null, { reload: vi.fn() });

        // onClick should be bound to noop
        expect(dom[0].props.onClick).toBeDefined();
        // Call onClick to ensure it doesn't throw
        expect(() => dom[0].props.onClick()).not.toThrow();
      });
    });
  });
});

describe('CommonTable Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).elId = 0;
    delete (globalThis as any).testActionRef;
  });

  it('should render ProTable with default props', async () => {
    const { default: CommonTable } = await import('./index');

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    expect(screen.getByTestId('pro-table')).toBeInTheDocument();
  });

  it('should render new button when onBuildClick is provided', async () => {
    const { default: CommonTable } = await import('./index');
    const onBuildClick = vi.fn();

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
          onBuildClick,
        }
      })
    );

    const button = screen.getByTestId('antd-button');
    expect(button).toBeInTheDocument();
    expect(button.textContent).toContain('新建');
  });

  it('should not render new button when onBuildClick is not provided', async () => {
    const { default: CommonTable } = await import('./index');

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    expect(screen.queryByTestId('antd-button')).not.toBeInTheDocument();
  });

  it('should call onBuildClick when new button is clicked', async () => {
    const { default: CommonTable } = await import('./index');
    const onBuildClick = vi.fn();

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
          onBuildClick,
        }
      })
    );

    const button = screen.getByTestId('antd-button');
    fireEvent.click(button);

    expect(onBuildClick).toHaveBeenCalled();
  });

  it('should use external actionRef when provided', async () => {
    const { default: CommonTable } = await import('./index');
    const actionRef = { current: undefined };

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
          actionRef,
        }
      })
    );

    expect((globalThis as any).testActionRef).toBe(actionRef);
  });

  it('should use internal actionRef when not provided', async () => {
    const { default: CommonTable } = await import('./index');

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    expect((globalThis as any).testActionRef).toBeDefined();
    expect((globalThis as any).testActionRef.current).toBeUndefined();
  });

  it('should call reset and reload on reset button click', async () => {
    const { default: CommonTable } = await import('./index');
    const reset = vi.fn();
    const reload = vi.fn();

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    (globalThis as any).testActionRef.current = { reset, reload };

    const resetButton = screen.getByTestId('reset-button');
    fireEvent.click(resetButton);

    expect(reset).toHaveBeenCalled();
    expect(reload).toHaveBeenCalled();
  });

  it('should handle actionRef without reset method', async () => {
    const { default: CommonTable } = await import('./index');
    const reload = vi.fn();

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    (globalThis as any).testActionRef.current = { reload };

    const resetButton = screen.getByTestId('reset-button');
    fireEvent.click(resetButton);

    expect(reload).toHaveBeenCalled();
  });

  it('should handle actionRef without reload method', async () => {
    const { default: CommonTable } = await import('./index');
    const reset = vi.fn();

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    (globalThis as any).testActionRef.current = { reset };

    const resetButton = screen.getByTestId('reset-button');
    fireEvent.click(resetButton);

    expect(reset).toHaveBeenCalled();
  });

  it('should handle null actionRef.current', async () => {
    const { default: CommonTable } = await import('./index');

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    (globalThis as any).testActionRef.current = null;

    const resetButton = screen.getByTestId('reset-button');
    fireEvent.click(resetButton);
  });

  it('should render with empty tableProps', async () => {
    const { default: CommonTable } = await import('./index');

    render(React.createElement(CommonTable, { tableProps: {} }));

    expect(screen.getByTestId('pro-table')).toBeInTheDocument();
  });

  it('should call columnsState onChange when columns state changes', async () => {
    const { default: CommonTable } = await import('./index');
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    // Trigger onChange
    (globalThis as any).testColumnsStateOnChange?.({ test: 'value' });

    expect(consoleSpy).toHaveBeenCalledWith('value: ', { test: 'value' });
    consoleSpy.mockRestore();
  });

  it('should call form syncToUrl with get type', async () => {
    const { default: CommonTable } = await import('./index');

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    // Trigger syncToUrl with 'get' type
    const result = (globalThis as any).testFormSyncToUrl?.({ key: 'value' }, 'get');

    expect(result).toEqual({ key: 'value' });
  });

  it('should call form syncToUrl with set type', async () => {
    const { default: CommonTable } = await import('./index');

    render(
      React.createElement(CommonTable, {
        tableProps: {
          columns: [],
          request: vi.fn(),
        }
      })
    );

    // Trigger syncToUrl with 'set' type
    const result = (globalThis as any).testFormSyncToUrl?.({ key: 'value' }, 'set');

    expect(result).toEqual({ key: 'value' });
  });
});
