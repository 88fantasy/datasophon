import { describe, expect, it } from 'vitest';
import {
  gcCollectionRateFormatter,
  gcTimeRateFormatter,
  requestRateFormatter,
} from './formatters';

describe('Gravitino monitor formatters', () => {
  it('keeps request, GC count, and GC time rates semantically distinct', () => {
    expect(requestRateFormatter(1.234)).toBe('1.23 req/s');
    expect(gcCollectionRateFormatter(1.234)).toBe('1.23 collections/s');
    expect(gcTimeRateFormatter(1.234)).toBe('1.23 ms/s');
  });
});
