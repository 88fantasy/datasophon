export const T_OUT = "out";

export const T_IN = "in";
export const invokeGenPort = (val) => {
  return [
    {
      id: `${val.id}-${T_OUT}`,
      group: T_OUT,
    },
    {
      id: `${val.id}-${T_IN}`,
      group: T_IN,
    },
  ];
};

export const invokeGenSourceAndTarget = (source, target) => {
  // const bakSource = source;
  // source = target;
  // target = bakSource;
  source = source && String(source);
  target = target && String(target);

  const res = {
    source: source && {
      cell: source,
      port: `${source}-${T_OUT}`,
    },
    target: target && {
      cell: target,
      port: `${target}-${T_IN}`,
    },
    zIndex: -1,
    data: {
      source,
      target,
    },
  };

  return res;
};
