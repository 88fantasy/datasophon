import { beforeEach, describe, expect, it, vi } from 'vitest';

const { animate, baseDestroy, cancel, register } = vi.hoisted(() => ({
  animate: vi.fn(),
  baseDestroy: vi.fn(),
  cancel: vi.fn(),
  register: vi.fn(),
}));

vi.mock('@antv/g6', () => ({
  CubicHorizontal: class {
    protected shapeMap = { key: { animate } };
    destroy() {
      baseDestroy();
    }
  },
  ExtensionCategory: { EDGE: 'edge' },
  register,
}));

import { FLOWING_LINEAGE_EDGE, FlowingLineageEdge } from './flowingLineageEdge';

describe('FlowingLineageEdge', () => {
  beforeEach(() => {
    animate.mockReset();
    baseDestroy.mockReset();
    cancel.mockReset();
    animate.mockReturnValue({ cancel });
  });

  it('registers a G6 edge extension', () => {
    expect(register).toHaveBeenCalledWith(
      'edge',
      FLOWING_LINEAGE_EDGE,
      FlowingLineageEdge,
    );
  });

  it('starts an infinite dash animation and cancels it on destroy', () => {
    const edge = new FlowingLineageEdge({} as never);

    edge.onCreate();
    edge.destroy();

    expect(animate).toHaveBeenCalledWith(
      [{ lineDashOffset: 20 }, { lineDashOffset: 0 }],
      { duration: 600, iterations: Infinity },
    );
    expect(cancel).toHaveBeenCalledTimes(1);
    expect(baseDestroy).toHaveBeenCalledTimes(1);
  });
});
