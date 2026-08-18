import {
  type RefObject,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';

const INITIAL_HEIGHT = 560;

interface FillViewportHeightOptions {
  /** 高度下限，避免小窗口下容器被挤到不可用 */
  minHeight?: number;
  /** 容器底部预留的间距 */
  bottomGap?: number;
  /**
   * 高度变化后的回调。G6 的 `autoResize` 只监听 `window.onresize`，容器自身高度变化
   * （例如上方 Alert 出现/消失）不会触发它，需要在这里手动 `graph.resize()`。
   */
  onHeightChange?: () => void;
}

/**
 * 让图容器填满视口剩余高度，返回应当应用到容器上的像素高度。
 *
 * 之所以实测容器的 `top` 偏移而不是写 `calc(100vh - Npx)`：容器上方的 clusterBar 与提示
 * Alert 数量会随状态变化，任何固定偏移量都会在少一个 Alert 时留白、多一个时溢出。
 *
 * @param deps 会影响容器 `top` 偏移的状态；它们变化时重新测量
 */
export function useFillViewportHeight(
  containerRef: RefObject<HTMLElement | null>,
  deps: unknown[],
  {
    minHeight = 480,
    bottomGap = 24,
    onHeightChange,
  }: FillViewportHeightOptions = {},
): number {
  const [height, setHeight] = useState(INITIAL_HEIGHT);

  // 回调存进 ref，这样它不必被调用方 useCallback 包裹也不会让 effect 反复重跑
  const onHeightChangeRef = useRef(onHeightChange);
  onHeightChangeRef.current = onHeightChange;

  useLayoutEffect(() => {
    const recompute = () => {
      if (!containerRef.current) return;
      const top = containerRef.current.getBoundingClientRect().top;
      setHeight(Math.max(minHeight, window.innerHeight - top - bottomGap));
    };
    recompute();
    window.addEventListener('resize', recompute);
    return () => window.removeEventListener('resize', recompute);
  }, [containerRef, minHeight, bottomGap, ...deps]);

  useEffect(() => {
    onHeightChangeRef.current?.();
  }, [height]);

  return height;
}
