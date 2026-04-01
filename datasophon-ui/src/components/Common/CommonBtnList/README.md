# CommonBtnList 组件

## 概述

`CommonBtnList` 是一个通用按钮列表组件，用于渲染多个操作按钮并管理其加载状态。该组件基于 Ant Design 的 Button 组件构建，提供了统一的按钮样式和加载状态管理。

## 功能特性

- **按钮列表渲染**: 根据传入的列表数据动态生成按钮
- **加载状态管理**: 自动跟踪和显示按钮的加载状态
- **异步操作支持**: 支持异步点击回调函数
- **灵活的按钮配置**: 支持 Ant Design Button 组件的所有属性

## Props 接口

| 属性 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `list` | `Array<ButtonConfig>` | 是 | 按钮配置数组 |

### ButtonConfig 对象结构

| 属性 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `label` | `string` | 是 | 按钮显示文本 |
| `onClick` | `() => void \| Promise<any>` | 否 | 按钮点击回调函数，支持异步操作 |
| `loading` | `boolean` | 否 | 按钮加载状态（如不传递，将由组件自动管理） |
| `...rest` | `Omit<ButtonProps, 'children'>` | 否 | Button 组件的其他属性（type、disabled、danger 等） |

## 使用示例

### 基础用法

```tsx
import CommonBtnList from '@/components/Common/CommonBtnList';

const buttons = [
  {
    label: '保存',
    onClick: async () => {
      await api.save();
    },
    type: 'primary'
  },
  {
    label: '删除',
    onClick: async () => {
      await api.delete();
    },
    type: 'primary',
    danger: true
  },
  {
    label: '取消',
    onClick: () => {
      // 处理取消逻辑
    }
  }
];

function MyComponent() {
  return <CommonBtnList list={buttons} />;
}
```

### 高级用法

```tsx
const buttons = [
  {
    label: '导出',
    onClick: async () => {
      const data = await fetchData();
      downloadFile(data);
    },
    type: 'primary'
  },
  {
    label: '更新',
    onClick: async () => {
      return await updateData();
    },
    type: 'default',
    disabled: isViewMode
  }
];
```

## 工作流程

1. 组件初始化时，创建一个 `loadingMap` 对象用于存储每个按钮的加载状态
2. 当用户点击按钮时：
   - 将对应按钮的 loading 状态设置为 `true`
   - 执行原始的 onClick 回调（支持异步）
   - 等待操作完成后，将 loading 状态设置为 `false`
3. 按钮的 loading 状态会自动绑定到 Ant Design Button 组件的 `loading` 属性

## 样式

组件使用 Tailwind CSS 类进行样式设置：
- 容器: `flex items-center gap-[10px]` - 弹性布局，按钮间距为 10px

## 注意事项

- ✅ 支持异步 onClick 回调
- ✅ 自动管理单个按钮的加载状态，互不影响
- ✅ 使用 `memo` 优化性能，避免不必要的重新渲染
- ✅ 支持自定义 loading 属性
- ⚠️ 如果手动传递 `loading` 属性，组件将不再管理该按钮的加载状态
- ⚠️ 确保 `list` 中每个按钮的 `label` 唯一，用作 loadingMap 的键

## 依赖项

- React (v16.8+)
- react-dom
- antd
- lodash-es

## 内部实现细节

- **memo**: 使用 `React.memo` 进行性能优化
- **useCallback**: 使用 `useCallback` 缓存 `invokeGenBtnList` 函数
- **clone**: 使用 `lodash-es` 的 `clone` 创建按钮配置副本，避免修改原始 props
- **cloneDeep**: 使用 `cloneDeep` 确保状态的深层复制，保证状态更新的正确性
