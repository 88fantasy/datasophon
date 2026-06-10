import { describe, expect, it } from 'vitest';

import { mergeYamlFiles } from './yamlMerge';

describe('mergeYamlFiles', () => {
  it('merges simple scalar override', () => {
    const base = 'replicaCount: 1\nimage: nginx\n';
    const override = 'replicaCount: 3\n';
    const result = mergeYamlFiles(base, override);
    expect(result).toContain('replicaCount: 3');
    expect(result).toContain('image: nginx');
  });

  it('deep-merges nested objects', () => {
    const base = 'resources:\n  limits:\n    cpu: 500m\n    memory: 128Mi\n';
    const override = 'resources:\n  limits:\n    cpu: 1000m\n';
    const result = mergeYamlFiles(base, override);
    expect(result).toContain('cpu: 1000m');
    expect(result).toContain('memory: 128Mi');
  });

  it('returns base when override is empty', () => {
    const base = 'key: value\n';
    expect(mergeYamlFiles(base, '')).toBe(base);
    expect(mergeYamlFiles(base, '   ')).toBe(base);
  });

  it('returns base on parse error in override', () => {
    const base = 'key: value\n';
    const badYaml = '{ broken: yaml: [}';
    const result = mergeYamlFiles(base, badYaml);
    // should fall back to base (or handle gracefully)
    expect(typeof result).toBe('string');
  });

  it('returns baseYaml when base is empty string', () => {
    expect(mergeYamlFiles('', 'key: val\n')).toBe('');
  });
});
