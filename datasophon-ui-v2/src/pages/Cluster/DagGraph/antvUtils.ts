/** x6 端口/连线生成工具，从 datasophon-ui/src/utils/antvUtils.ts 迁移 */

export const T_OUT = 'out';
export const T_IN = 'in';

export const invokeGenPort = (val: { id: string | number }) => {
  return [
    { id: `${val.id}-${T_OUT}`, group: T_OUT },
    { id: `${val.id}-${T_IN}`, group: T_IN },
  ];
};

export const invokeGenSourceAndTarget = (
  source: string | number | undefined,
  target: string | number | undefined,
) => {
  const src = source != null ? String(source) : undefined;
  const tgt = target != null ? String(target) : undefined;
  return {
    source: src ? { cell: src, port: `${src}-${T_OUT}` } : undefined,
    target: tgt ? { cell: tgt, port: `${tgt}-${T_IN}` } : undefined,
    zIndex: -1,
    data: { source: src, target: tgt },
  };
};
