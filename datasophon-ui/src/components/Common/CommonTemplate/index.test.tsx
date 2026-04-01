import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock dependencies
vi.mock('@ant-design/colors', () => ({
  grey: ['#ffffff', '#f0f0f0', '#d9d9d9'],
}));

vi.mock('antd', () => ({
  Col: vi.fn(({ children }) => <div>{children}</div>),
}));

vi.mock('../../../utils/util', () => ({
  isEmpty: vi.fn((val) => val === null || val === undefined || val === ''),
}));

vi.mock('lodash-es', () => ({
  cloneDeep: vi.fn((val) => JSON.parse(JSON.stringify(val))),
}));

vi.mock('./utils', () => ({
  invokeMapShowMultiply: vi.fn((item) => {
    const inputStringArray = item.type === "input" && item.configType === "stringArray";
    return ["multiple"].includes(item.type) || inputStringArray;
  }),
}));

vi.mock('@ant-design/pro-components', () => {
  const ProFormText = vi.fn(({ name }) => <div data-testid="pro-form-text" data-name={name}>TextInput</div>);
  ProFormText.Password = vi.fn(({ name }) => <div data-testid="pro-form-password" data-name={name}>PasswordInput</div>);
  
  return {
    ProCard: vi.fn(({ children, extra, title }) => (
      <div data-testid="pro-card" data-title={title}>
        {children}
        {extra}
      </div>
    )),
    ProForm: vi.fn(({ children }) => <div data-testid="pro-form">{children}</div>),
    ProFormGroup: vi.fn(({ children }) => <div data-testid="pro-form-group">{children}</div>),
    ProFormList: vi.fn(({ children, name, itemRender, rules }) => {
      // Store rules for testing validators
      if (rules) {
        (globalThis as any).lastFormListRules = rules;
      }
      // Execute itemRender if provided
      if (itemRender) {
        const result = itemRender(
          { listDom: children, action: <div>action</div> },
          { record: {}, index: 0 }
        );
        return <div data-testid="pro-form-list" data-name={name}>{result}</div>;
      }
      return <div data-testid="pro-form-list" data-name={name}>{children}</div>;
    }),
    ProFormSelect: vi.fn(({ name, mode, options }) => (
      <div data-testid="pro-form-select" data-name={name} data-mode={mode}>
        <select>{options?.map((opt: any) => <option key={opt.value}>{opt.label}</option>)}</select>
      </div>
    )),
    ProFormSlider: vi.fn(({ name, fieldProps }) => (
      <div data-testid="pro-form-slider" data-name={name}>
        <input type="range" min={fieldProps?.min} max={fieldProps?.max} />
      </div>
    )),
    ProFormSwitch: vi.fn(({ name }) => <div data-testid="pro-form-switch" data-name={name}>Switch</div>),
    ProFormText,
  };
});

import CommonTemplate from './index';

describe('CommonTemplate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Basic rendering', () => {
    it('should render with empty templateData', () => {
      const { container } = render(<CommonTemplate templateData={[]} />);
      expect(container.firstChild).toBeInTheDocument();
    });

    it('should render with className', () => {
      const { container } = render(<CommonTemplate templateData={[]} className="custom-class" />);
      expect(container.querySelector('.custom-class')).toBeInTheDocument();
    });

    it('should render with namePrefix', () => {
      const templateData = [{ name: 'test', label: 'Test', type: 'input' }];
      render(<CommonTemplate templateData={templateData} namePrefix={['prefix']} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });

    it('should filter out disabled items', () => {
      const templateData = [
        { name: 'enabled', label: 'Enabled', type: 'input', enabled: true },
        { name: 'disabled', label: 'Disabled', type: 'input', enabled: false }
      ];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formText = container.querySelectorAll('[data-testid="pro-form-text"]');
      expect(formText.length).toBe(1);
    });

    it('should render label without name', () => {
      const templateData = [{ label: 'Simple Label', type: 'input' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });

    it('should render label with name', () => {
      const templateData = [{ name: 'username', label: '用户名', type: 'input' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });
  });

  describe('Input field', () => {
    it('should render input field', () => {
      const templateData = [{ name: 'username', label: '用户名', type: 'input' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });

    it('should render input with value', () => {
      const templateData = [{ name: 'username', label: '用户名', type: 'input', value: 'test' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });

    it('should render input with defaultValue', () => {
      const templateData = [{ name: 'username', label: '用户名', type: 'input', defaultValue: 'test' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });

    it('should render input with required', () => {
      const templateData = [{ name: 'username', label: '用户名', type: 'input', required: true }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });

    it('should render input with placeholder', () => {
      const templateData = [{ name: 'username', label: '用户名', type: 'input', placeholder: '请输入' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });

    it('should render input with description', () => {
      const templateData = [{ name: 'username', label: '用户名', type: 'input', description: '描述' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-text')).toBeInTheDocument();
    });
  });

  describe('Password field', () => {
    it('should render password field', () => {
      const templateData = [{ name: 'password', label: '密码', type: 'password' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-password')).toBeInTheDocument();
    });

    it('should render password with required', () => {
      const templateData = [{ name: 'password', label: '密码', type: 'password', required: true }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-password')).toBeInTheDocument();
    });
  });

  describe('Slider field', () => {
    it('should render slider field', () => {
      const templateData = [{ name: 'size', label: '大小', type: 'slider', minValue: 0, maxValue: 100 }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-slider')).toBeInTheDocument();
    });

    it('should render slider with custom min/max', () => {
      const templateData = [{ name: 'size', label: '大小', type: 'slider', minValue: 10, maxValue: 50 }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-slider')).toBeInTheDocument();
    });
  });

  describe('Switch field', () => {
    it('should render switch field', () => {
      const templateData = [{ name: 'enabled', label: '启用', type: 'switch' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-switch')).toBeInTheDocument();
    });

    it('should render switch with value', () => {
      const templateData = [{ name: 'enabled', label: '启用', type: 'switch', value: true }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-switch')).toBeInTheDocument();
    });
  });

  describe('Select field', () => {
    it('should render select field', () => {
      const templateData = [{ name: 'role', label: '角色', type: 'select', selectValue: ['Admin', 'User'] }];
      render(<CommonTemplate templateData={templateData} />);
      const select = screen.getByTestId('pro-form-select');
      expect(select).toBeInTheDocument();
      expect(select.dataset.mode).toBe('single');
    });

    it('should render select with empty options', () => {
      const templateData = [{ name: 'role', label: '角色', type: 'select', selectValue: [] }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-select')).toBeInTheDocument();
    });

    it('should render select without selectValue', () => {
      const templateData = [{ name: 'role', label: '角色', type: 'select' }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-select')).toBeInTheDocument();
    });

    it('should render select with required', () => {
      const templateData = [{ name: 'role', label: '角色', type: 'select', selectValue: ['Admin'], required: true }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-select')).toBeInTheDocument();
    });
  });

  describe('MultipleSelect field', () => {
    it('should render multipleSelect field', () => {
      const templateData = [{ name: 'tags', label: '标签', type: 'multipleSelect', selectValue: ['Tag1', 'Tag2'] }];
      render(<CommonTemplate templateData={templateData} />);
      const select = screen.getByTestId('pro-form-select');
      expect(select).toBeInTheDocument();
      expect(select.dataset.mode).toBe('multiple');
    });

    it('should render multipleSelect with required', () => {
      const templateData = [{ name: 'tags', label: '标签', type: 'multipleSelect', selectValue: ['Tag1'], required: true }];
      render(<CommonTemplate templateData={templateData} />);
      expect(screen.getByTestId('pro-form-select')).toBeInTheDocument();
    });
  });

  describe('Multiple field (invokeMapShowMultiply)', () => {
    it('should render multiple field', () => {
      const templateData = [{ name: 'emails', label: '邮箱', type: 'multiple' }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multiple with required', () => {
      const templateData = [{ name: 'emails', label: '邮箱', type: 'multiple', required: true }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render input with configType stringArray', () => {
      const templateData = [{ name: 'tags', label: '标签', type: 'input', configType: 'stringArray' }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should call multiple validator - throw error when required and empty', async () => {
      const templateData = [{ name: 'emails', label: '邮箱', type: 'multiple', required: true }];
      render(<CommonTemplate templateData={templateData} />);
      
      const rules = (globalThis as any).lastFormListRules;
      if (rules && rules[0]?.validator) {
        try {
          await rules[0].validator(null, [{ value: '' }]);
          expect(true).toBe(false);
        } catch (e: any) {
          expect(e.message).toBe('至少要有一项！');
        }
        
        await expect(rules[0].validator(null, [{ value: 'test@test.com' }])).resolves.toBeUndefined();
      }
    });

    it('should call multiple validator - pass when not required', async () => {
      const templateData = [{ name: 'emails', label: '邮箱', type: 'multiple', required: false }];
      render(<CommonTemplate templateData={templateData} />);
      
      const rules = (globalThis as any).lastFormListRules;
      if (rules && rules[0]?.validator) {
        await expect(rules[0].validator(null, [])).resolves.toBeUndefined();
      }
    });
  });

  describe('MultipleWithKey field', () => {
    it('should render multipleWithKey field', () => {
      const templateData = [{ name: 'envVars', label: '环境变量', type: 'multipleWithKey' }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multipleWithKey with required', () => {
      const templateData = [{ name: 'envVars', label: '环境变量', type: 'multipleWithKey', required: true }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multipleWithKey with value', () => {
      const templateData = [{ name: 'envVars', label: '环境变量', type: 'multipleWithKey', value: [{ key: 'K1', value: 'V1' }] }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should call multipleWithKey validator - throw error when required and empty', async () => {
      const templateData = [{ name: 'envVars', label: '环境变量', type: 'multipleWithKey', required: true }];
      render(<CommonTemplate templateData={templateData} />);
      
      const rules = (globalThis as any).lastFormListRules;
      if (rules && rules[0]?.validator) {
        try {
          await rules[0].validator(null, []);
          expect(true).toBe(false);
        } catch (e: any) {
          expect(e.message).toBe('至少要有一项！');
        }
        
        await expect(rules[0].validator(null, [{ key: 'K', value: 'V' }])).resolves.toBeUndefined();
      }
    });

    it('should call multipleWithKey validator - pass when not required', async () => {
      const templateData = [{ name: 'envVars', label: '环境变量', type: 'multipleWithKey', required: false }];
      render(<CommonTemplate templateData={templateData} />);
      
      const rules = (globalThis as any).lastFormListRules;
      if (rules && rules[0]?.validator) {
        await expect(rules[0].validator(null, [])).resolves.toBeUndefined();
      }
    });
  });

  describe('MultipleWithMap field', () => {
    it('should render multipleWithMap field without value', () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap' }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multipleWithMap with required', () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap', required: true }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multipleWithMap with value array', () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap', value: [{ key1: 'v1', key2: 'v2' }] }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multipleWithMap with empty value array', () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap', value: [] }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multipleWithMap with null value', () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap', value: null }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should render multipleWithMap with value containing items', () => {
      const templateData = [{
        name: 'config',
        label: '配置',
        type: 'multipleWithMap',
        value: [{ items: [{ key: 'k1', value: 'v1' }] }]
      }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should call multipleWithMap validator - throw error when required and empty', async () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap', required: true }];
      render(<CommonTemplate templateData={templateData} />);
      
      // Get the stored rules from mock - the last ProFormList should have the multipleWithMap rules
      const rules = (globalThis as any).lastFormListRules;
      if (rules && rules[0]?.validator) {
        // Call validator with empty array - should throw
        try {
          await rules[0].validator(null, []);
          // Should not reach here
          expect(true).toBe(false);
        } catch (e: any) {
          expect(e.message).toBe('至少要有一项！');
        }
        
        // Call validator with non-empty array - should pass
        await expect(rules[0].validator(null, [{ key: 'k', value: 'v' }])).resolves.toBeUndefined();
      }
    });

    it('should call multipleWithMap validator - pass when not required', async () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap', required: false }];
      render(<CommonTemplate templateData={templateData} />);
      
      const rules = (globalThis as any).lastFormListRules;
      if (rules && rules[0]?.validator) {
        await expect(rules[0].validator(null, [])).resolves.toBeUndefined();
      }
    });

    it('should render multipleWithMap with itemRender', () => {
      const templateData = [{
        name: 'config',
        label: '配置',
        type: 'multipleWithMap',
        required: true,
        value: [{ items: [{ key: 'k1', value: 'v1' }] }]
      }];
      const { container } = render(<CommonTemplate templateData={templateData} />);
      const formLists = container.querySelectorAll('[data-testid="pro-form-list"]');
      expect(formLists.length).toBeGreaterThanOrEqual(1);
    });

    it('should call multipleWithMap validator', async () => {
      const templateData = [{ name: 'config', label: '配置', type: 'multipleWithMap', required: true }];
      render(<CommonTemplate templateData={templateData} />);
      
      // Get the stored rules from mock
      const rules = (globalThis as any).lastFormListRules;
      if (rules && rules[0]?.validator) {
        // Call validator with empty array - should throw when required
        try {
          await rules[0].validator(null, []);
          expect(true).toBe(false);
        } catch (e: any) {
          expect(e.message).toBe('至少要有一项！');
        }
        
        // Call validator with non-empty array - should pass
        await expect(rules[0].validator(null, [{ items: [] }])).resolves.toBeUndefined();
      }
    });
  });

  describe('Edge cases', () => {
    it('should render default empty className', () => {
      const { container } = render(<CommonTemplate templateData={[]} />);
      const div = container.querySelector('div');
      expect(div?.className).toBe('');
    });
  });
});