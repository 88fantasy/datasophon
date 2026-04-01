# CommonTabs 组件

## 概述

`CommonTabs` 是一个增强型标签页组件，基于 Ant Design 的 `Tabs` 组件构建，提供了 URL 绑定、懒加载、切换拦截等高级功能。

## 功能特性

- **URL 绑定** - 将当前激活的标签同步到 URL 查询参数，支持刷新恢复
- **懒加载** - 支持使用 `asyncChildren` 实现标签内容懒加载，提升大型应用性能
- **切换拦截** - 支持 `onBeforeChange` 回调阻止标签切换，防止误操作
- **自动状态管理** - 自动维护激活状态，支持多种初始化方式
- **操作列处理** - 自动包装 Suspense，传递 props 和 ref

## Props 接口

### CommonTabs 组件 Props

```typescript
interface CommonTabsProps {
  memoTabItem: TabItem[];  // 必填，标签页配置数组
  bindUrl?: boolean;      // 是否将当前标签状态同步到 URL
  tabKey?: string;        // URL 查询参数的键名，默认 'tab'
  defActiveKey?: string;  // 默认激活的标签页 key
  onBeforeChange?: (params: { current: string; next: string }) => Promise<boolean> | boolean;
  destroyOnHidden?: boolean;  // 隐藏时是否销毁标签内容，默认 true
  tabBarExtraContent?: ReactNode;  // 标签栏额外内容
  rootClassName?: string;   // 根元素自定义类名
  type?: 'line' | 'card' | 'editable-card';  // 标签页类型
}
```

### TabItem 对象结构

```typescript
interface TabItem {
  key: string;                              // 必填，唯一标识
  label: ReactNode;                        // 必填，标签显示文本
  children?: ReactNode;                    // 标签内容（与 asyncChildren 二选一）
  asyncChildren?: LazyExoticComponent;     // 懒加载组件
  props?: object;                          // 传递给懒加载组件的 props
  ref?: Ref;                               // 传递给懒加载组件的 ref
}
```

## 使用示例

### 基础用法

```tsx
import CommonTabs from '@/components/Common/CommonTabs';

const items = [
  { key: 'tab1', label: '标签1', children: <div>内容1</div> },
  { key: 'tab2', label: '标签2', children: <div>内容2</div> },
];

function MyComponent() {
  return <CommonTabs memoTabItem={items} />;
}
```

### URL 绑定

```tsx
<CommonTabs 
  memoTabItem={items} 
  bindUrl={true}
  tabKey="activeTab"
/>
// URL: /path?activeTab=tab1
```

### 懒加载

```tsx
const HeavyComponent = React.lazy(() => import('./HeavyComponent'));

const items = [
  { 
    key: 'lazy', 
    label: '懒加载标签', 
    asyncChildren: HeavyComponent,
    props: { someProp: 'value' }
  }
];

<CommonTabs memoTabItem={items} />
```

### 切换拦截

```tsx
<CommonTabs 
  memoTabItem={items}
  onBeforeChange={({ current, next }) => {
    if (current === 'edit' && hasUnsavedChanges) {
      return window.confirm('有未保存的更改，确定切换吗？')
    }
    return true
  }}
/>
```

### 卡片样式

```tsx
<CommonTabs 
  memoTabItem={items} 
  type="card"
  tabBarExtraContent={<Button type="primary">刷新</Button>}
/>
```

## 工作流程

### 标签初始化

1. 组件接收 `memoTabItem` 配置数组
2. 根据优先级确定初始激活状态：`defActiveKey` > URL 查询参数 > 第一个标签
3. 使用 `useMemo` 处理标签项，过滤 falsy 值
4. 为使用 `asyncChildren` 的标签包装 Suspense

### 标签切换

1. 用户点击标签时触发 `onChange` 回调
2. 调用 `onBeforeChange` 钩子（如果提供）
3. 如果返回 `false`，阻止切换
4. 如果 `bindUrl` 为 `true`，更新 URL 查询参数
5. 更新内部 `activeKey` 状态

### 懒加载处理

1. 检测 `asyncChildren` 属性
2. 使用 `Suspense` 包装懒加载组件
3. 传递 `props` 和 `ref` 给组件
4. 显示 `Spin` 加载指示器

## 样式和布局

- **加载指示器**: Spin 组件居中显示 (`left-[50%] top-[50%] transform-[translate(-50%,-50%)]`)
- **默认类型**: 使用 Ant Design Tabs 默认样式
- **自定义**: 通过 `rootClassName` 和 `type` 属性定制

## 内置特性

| 特性 | 说明 |
|------|------|
| **URL 同步** | 激活标签自动保存到 URL，支持刷新恢复 |
| **懒加载** | 使用 React.lazy 和 Suspense 实现 |
| **切换确认** | 支持切换前确认，防止误操作 |
| **状态持久化** | 列状态自动保存到 localStorage |
| **Memo 优化** | 使用 `React.memo` 避免不必要的重新渲染 |

## 依赖项

- React
- react-dom
- antd (Tabs, Spin)
- lodash-es (cloneDeep)
- qs (URL 参数解析)

## 注意事项

- ✅ 支持 URL 状态同步和恢复
- ✅ 支持懒加载组件，提升大型应用性能
- ✅ 支持切换前确认，防止误操作
- ✅ 自动管理激活状态，支持多种初始化方式
- ⚠️ 确保 `memoTabItem` 中每个标签的 `key` 唯一
- ⚠️ 懒加载组件会显示 Spin 加载指示器
- ⚠️ `bindUrl` 为 `true` 时，需要确保路由库正常工作

## 内部实现细节

- **memo**: 使用 `React.memo` 进行性能优化
- **useState**: 管理激活标签的内部状态
- **useCallback**: 缓存 `onChange` 回调函数
- **useMemo**: 缓存处理后的标签项数组
- **useEffect**: 监听标签项变化，确保 activeKey 有效性
- **Suspense**: 为懒加载组件提供加载状态
- **cloneDeep**: 使用 lodash-es 的 `cloneDeep` 确保数据不可变
- **getRouteQuery**: 从 URL 获取查询参数
- **replaceRouter**: 替换 URL 查询参数