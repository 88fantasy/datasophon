import { describe, expect, it } from 'vitest';
import {
  dumpGatewayYaml,
  findDanglingUpstreamRefs,
  findDuplicateIds,
  hasComments,
  isBuiltinGlobalRule,
  loadGatewayYaml,
  removeGlobalRule,
  upsertById,
} from './gatewayYaml';

describe('loadGatewayYaml / dumpGatewayYaml', () => {
  it('round-trips a typical doc', () => {
    const text = 'routes:\n  - id: 1\n    uri: /get\n';
    const doc = loadGatewayYaml(text);
    expect(doc.routes).toEqual([{ id: 1, uri: '/get' }]);
    expect(dumpGatewayYaml(doc)).toContain('uri: /get');
  });

  it('returns empty map for empty text', () => {
    expect(loadGatewayYaml('')).toEqual({});
  });

  it('throws when top level is not a map', () => {
    expect(() => loadGatewayYaml('- a\n- b\n')).toThrow();
    expect(() => loadGatewayYaml('just a string')).toThrow();
  });

  it('preserves unknown top-level sections and unknown fields on round trip', () => {
    const text =
      'routes:\n  - id: 1\n    uri: /get\n    custom_field: kept\nconsumers:\n  - username: alice\n';
    const doc = loadGatewayYaml(text);
    const dumped = dumpGatewayYaml(doc);
    const reloaded = loadGatewayYaml(dumped);
    expect(reloaded.consumers).toEqual([{ username: 'alice' }]);
    expect((reloaded.routes as any[])[0].custom_field).toBe('kept');
  });
});

describe('isBuiltinGlobalRule / removeGlobalRule', () => {
  it('treats id 1 and 2 as builtin (prometheus / opentelemetry)', () => {
    expect(isBuiltinGlobalRule({ id: 1 })).toBe(true);
    expect(isBuiltinGlobalRule({ id: '2' })).toBe(true);
    expect(isBuiltinGlobalRule({ id: 3 })).toBe(false);
  });

  it('removeGlobalRule keeps builtin rules even when asked to remove them', () => {
    const rules = [
      { id: 1, plugins: { prometheus: {} } },
      { id: 2, plugins: { opentelemetry: {} } },
      { id: 3, plugins: { 'limit-count': {} } },
    ];
    expect(removeGlobalRule(rules, 1).map((r) => r.id)).toEqual([1, 2, 3]);
    expect(removeGlobalRule(rules, 3).map((r) => r.id)).toEqual([1, 2]);
  });
});

describe('upsertById', () => {
  it('merges instead of replacing, keeping fields the form does not know about', () => {
    const list = [{ id: 1, uri: '/get', unknownField: 'kept' }];
    const next = upsertById(list, { id: 1, uri: '/post' } as any);
    expect(next[0]).toEqual({ id: 1, uri: '/post', unknownField: 'kept' });
  });

  it('appends when id not found', () => {
    const next = upsertById([{ id: 1 }], { id: 2 });
    expect(next.map((i) => i.id)).toEqual([1, 2]);
  });
});

describe('findDuplicateIds', () => {
  it('detects duplicate ids', () => {
    expect(findDuplicateIds([{ id: 1 }, { id: 2 }, { id: 1 }])).toEqual(['1']);
    expect(findDuplicateIds([{ id: 1 }, { id: 2 }])).toEqual([]);
  });
});

describe('findDanglingUpstreamRefs', () => {
  it('flags routes referencing a missing upstream', () => {
    const doc = {
      upstreams: [{ id: 1 }],
      routes: [
        { id: 1, uri: '/ok', upstream_id: 1 },
        { id: 2, uri: '/broken', upstream_id: 99 },
      ],
    };
    expect(findDanglingUpstreamRefs(doc)).toEqual(['/broken']);
  });

  it('returns empty when all references resolve', () => {
    const doc = {
      upstreams: [{ id: 1 }],
      routes: [{ id: 1, uri: '/ok', upstream_id: 1 }],
    };
    expect(findDanglingUpstreamRefs(doc)).toEqual([]);
  });
});

describe('hasComments', () => {
  it('detects a real comment line', () => {
    expect(hasComments('routes: []\n# a comment\n')).toBe(true);
  });

  it('ignores # inside quoted strings', () => {
    expect(hasComments("uri: '/a#b'\n")).toBe(false);
  });
});
