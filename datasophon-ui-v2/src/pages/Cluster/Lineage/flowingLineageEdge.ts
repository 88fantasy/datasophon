import type { IAnimation } from '@antv/g';
import { CubicHorizontal, ExtensionCategory, register } from '@antv/g6';

export const FLOWING_LINEAGE_EDGE = 'lineage-flowing-edge';

export class FlowingLineageEdge extends CubicHorizontal {
  private flowAnimation: IAnimation | null = null;

  onCreate() {
    this.flowAnimation = this.shapeMap.key.animate(
      [{ lineDashOffset: 20 }, { lineDashOffset: 0 }],
      { duration: 600, iterations: Infinity },
    );
  }

  destroy() {
    this.flowAnimation?.cancel();
    this.flowAnimation = null;
    super.destroy();
  }
}

register(ExtensionCategory.EDGE, FLOWING_LINEAGE_EDGE, FlowingLineageEdge);
