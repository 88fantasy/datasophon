export const requestRateFormatter = (value: number) =>
  `${value.toFixed(2)} req/s`;

export const gcCollectionRateFormatter = (value: number) =>
  `${value.toFixed(2)} collections/s`;

export const gcTimeRateFormatter = (value: number) =>
  `${value.toFixed(2)} ms/s`;
