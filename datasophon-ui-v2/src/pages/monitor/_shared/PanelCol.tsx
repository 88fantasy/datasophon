import type { ColProps } from 'antd';
import { Col } from 'antd';
import type { FC, ReactNode } from 'react';

interface PanelColProps extends Omit<ColProps, 'span' | 'xs' | 'md' | 'lg'> {
  /** 桌面(≥lg)下的栅格跨度，与 antd `<Col span>` 同义。 */
  span: number;
  children?: ReactNode;
}

/**
 * 监控看板栅格列：把响应式断点收敛到组件内部，替代旧版 monitorStyles 里
 * 针对 `.ant-col-4/6/8/12/16` 内部类的媒体查询 hack（耦合 antd 内部实现，易随版本失效）。
 *
 * 断点严格复刻原 CSS 行为（移动优先映射）：
 * - `<md`(<768)：整行 24；
 * - `md`(768–992)：4/6 → 8(1/3)，8/12/16 → 12(1/2)，24 保持 24；
 * - `≥lg`(992)：声明的 span。
 */
const PanelCol: FC<PanelColProps> = ({ span, children, ...rest }) => (
  <Col xs={24} md={span <= 6 ? 8 : span >= 24 ? 24 : 12} lg={span} {...rest}>
    {children}
  </Col>
);

export default PanelCol;
