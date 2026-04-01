# CommonTable 组件

## 概述

`CommonTable` 是一个高度可定制的表格组件，基于 Ant Design Pro 的 `ProTable` 构建，提供了开箱即用的表格功能，包括搜索、列配置、操作列处理等。该组件特别针对常见的数据表格操作（如编辑、删除）进行了优化。

## 功能特性

- **基于 ProTable** - 继承 ProTable 的所有强大功能
- **智能操作列生成** - 自动处理删除、编辑等常见操作
- **列状态持久化** - 表格列配置自动保存到 localStorage
- **搜索功能** - 集成搜索和过滤功能
- **工具栏** - 支持自定义工具栏（如"新建"按钮）
- **删除确认** - 删除操作前自动弹出确认对话框
- **编辑支持** - 集成表单编辑功能
- **响应式** - 支持水平和竖直滚动

## Props 接口

### CommonTable 组件 Props

```typescript
interface Props {
  tableProps: ProTableProps & {
    // 自定义属性
    onBuildClick?: (params: { action: ActionType }) => void;
    actionRef?: React.MutableRefObject<ActionType | undefined>;
  }
}
```

#### `tableProps`

继承 `ProTable` 的所有属性，并添加以下自定义属性：

| 属性 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `columns` | `ColumnType[]` | 是 | 表格列配置，参考 ProTable 文档 |
| `request` | `(params) => Promise<any>` | 是 | 请求数据的函数 |
| `onBuildClick` | `(params) => void` | 否 | "新建"按钮点击回调 |
| `actionRef` | `RefObject<ActionType>` | 否 | 表格引用，用于控制表格操作 |
| 其他 | - | 否 | ProTable 的所有其他属性 |

## invokeGenOptionCol 函数

### 用途

生成表格操作列（option column），自动处理删除、编辑等常见操作。

### 语法

```typescript
invokeGenOptionCol(list, config?)
```

### 参数

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `list` | `ColumnType[]` | 是 | 完整的列配置数组，用于编辑表单 |
| `config` | `OperationConfig \| () => OperationConfig` | 否 | 操作配置，可以是对象或返回对象的函数 |

### OperationConfig 配置

```typescript
interface OperationConfig {
  pure?: boolean;  // 是否为纯函数模式，默认为 false
}
```

### 操作项配置

操作项的结构：

```typescript
interface OperationItem {
  label?: string;      // 操作标签
  title?: string;      // 操作标题
  key?: string;        // 操作标识
  disabled?: boolean | (...args) => boolean;  // 是否禁用
  onClick?: (...args) => Promise<any>;        // 点击回调
  titleKey?: string;   // 用于确认对话框的字段名
  config?: object;     // 编辑操作的表单配置
}
```

### 返回值

返回一个函数（或数组），用于生成操作列的渲染内容。

### 自动处理的操作

1. **删除操作** - 标题包含 "delete" 或 "删除"
   - 自动弹出确认对话框
   - 操作完成后自动刷新表格

2. **编辑操作** - 标题包含 "edit" 或 "编辑" 且传入 config
   - 自动打开编辑表单
   - 表单配置由 config 提供

## 使用示例

### 基础用法

```tsx
import CommonTable from '@/components/Common/CommonTable';

const columns = [
  {
    title: '名称',
    dataIndex: 'name',
    valueType: 'text',
  },
  {
    title: '描述',
    dataIndex: 'description',
    valueType: 'textarea',
  },
  {
    title: '操作',
    valueType: 'option',
    render: (text, record, _, action) => {
      return invokeGenOptionCol(columns, {
        pure: false
      })([
        {
          title: '编辑',
          key: 'edit',
          config: { /* 编辑表单配置 */ },
          onClick: async (text, record) => {
            // 编辑逻辑
          },
        },
        {
          title: '删除',
          key: 'delete',
          titleKey: 'name',
          onClick: async (text, record) => {
            const response = await api.delete(record.id);
            return response;
          },
        },
      ])(text, record, _, action);
    },
  },
];

export function MyTable() {
  return (
    <CommonTable
      tableProps={{
        columns,
        request: async (params) => {
          const response = await api.getList(params);
          return response;
        },
        onBuildClick: ({ action }) => {
          // 处理新建按钮点击
          console.log('新建按钮被点击');
        },
      }}
    />
  );
}
```

### 高级用法 - 带编辑表单

```tsx
import { invokeGenOptionCol } from '@/components/Common/CommonTable';

const columns = [
  // ... 列配置
];

const operationRender = (text, record, _, action) => {
  return invokeGenOptionCol(columns)([
    {
      title: '编辑',
      key: 'edit',
      disabled: record.locked, // 根据条件禁用
      config: {
        // 编辑表单配置
        initialValues: record,
      },
      onClick: async (text, record, _, action, formData) => {
        const response = await api.update(record.id, formData);
        return response;
      },
    },
    {
      title: '删除',
      key: 'delete',
      titleKey: 'name',
      disabled: (text, record) => record.protected,
      onClick: async (text, record) => {
        return await api.delete(record.id);
      },
    },
  ])(text, record, _, action);
};
```

## 工作流程

### 表格初始化
1. 组件接收 `tableProps` 配置
2. 初始化内部或外部的 `ActionType` 引用
3. 配置列状态持久化（localStorage）
4. 渲染 ProTable 组件

### 搜索和重置
1. 用户输入搜索条件后，自动调用 `request` 函数
2. 点击重置按钮时，调用 `onReset` 回调
3. 重置后自动重新加载数据

### 操作列处理
1. 调用 `invokeGenOptionCol` 生成操作列
2. 根据操作类型自动处理：
   - **删除**: 弹出确认 → 执行删除 → 刷新表格
   - **编辑**: 打开表单 → 提交表单 → 刷新表表或执行自定义逻辑

## 样式和布局

- **高度**: 表格内容区域高度为 50vh（视口高度的 50%）
- **布局**: 卡片式布局（cardBordered）
- **列配置**: 列设置面板高度为 400px
- **选项列**: 固定在表格右侧

## 内置特性

| 特性 | 说明 |
|------|------|
| **列持久化** | 用户调整的列顺序/可见性保存到 localStorage |
| **搜索标签** | 搜索标签宽度自动适应 |
| **日期格式化** | 日期字段自动格式化为字符串 |
| **行键** | 使用 `id` 作为行唯一标识 |
| **编辑模式** | 支持多行编辑模式 |

## 依赖项

- React (v16.8+)
- react-dom
- antd
- @ant-design/pro-components
- lodash-es

## 注意事项

- ✅ 支持自定义 actionRef 或使用内部 actionRef
- ✅ 删除操作自动确认并刷新表格
- ✅ 编辑操作支持自定义表单配置
- ✅ 列配置自动持久化到 localStorage
- ⚠️ 需要在 columns 中提供 `valueType: 'option'` 的操作列
- ⚠️ 删除/编辑操作的标题需要包含对应的中文或英文关键词
- ⚠️ 编辑操作需要在操作项中提供 `config` 对象

## 高级配置

### 列状态持久化

列配置自动保存到 localStorage，key 为 `pro-table-singe-demos`：

```typescript
// 用户选择的列配置会自动保存
// 重新加载页面时会自动恢复
```

### 自定义新建按钮行为

```tsx
<CommonTable
  tableProps={{
    // ...
    onBuildClick: ({ action }) => {
      // action 是表格的 ActionType 引用
      // 可以用于操作表格（刷新、重置等）
      openCreateModal();
    },
  }}
/>
```

### 使用外部 actionRef

```tsx
const tableRef = useRef<ActionType>();

<CommonTable
  tableProps={{
    actionRef: tableRef,
    // ...
  }}
/>

// 在其他地方控制表格
tableRef.current?.reload();
tableRef.current?.reset();
```

## API 文档

### showComfirmModal(options)

显示确认对话框。

**参数**: 
- `content` (string) - 对话框内容
- `okType` (string) - 确定按钮类型（如 'danger'）

**返回**: `Promise<boolean>` - 用户是否确认

### showMsgAfferRequest(response)

请求后显示消息。

**参数**:
- `response` (object) - 请求的响应对象

**返回**: 根据响应内容显示成功或错误消息

