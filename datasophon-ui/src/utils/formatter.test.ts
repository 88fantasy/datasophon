import { describe, it, expect } from "vitest";
import { formatConfig } from "./formatter";

describe("formatConfig", () => {
  it("should format simple object with string, number, boolean", () => {
    const obj = { name: "test", age: 25, active: true };
    const result = formatConfig(obj);
    expect(result).toBe(`{
  name: 'test',
  age: 25,
  active: true
}`);
  });

  it("should format nested object", () => {
    const obj = { outer: { inner: "value" } };
    const result = formatConfig(obj);
    expect(result).toBe(`{
  outer: {
    inner: 'value'
  }
}`);
  });

  it("should format simple array", () => {
    const arr = [1, 2, 3];
    const result = formatConfig(arr);
    expect(result).toBe(`[1,2,3]`);
  });

  it("should format array with strings", () => {
    const arr = ["a", "b", "c"];
    const result = formatConfig(arr);
    expect(result).toBe(`['a','b','c']`);
  });

  it("should format array with objects", () => {
    const arr = [{ id: 1 }, { id: 2 }];
    const result = formatConfig(arr);
    expect(result).toBe(`[
  {
    id: 1
  },
  {
    id: 2
  }
]`);
  });

  it("should ignore functions in objects", () => {
    const obj = { name: "test", method: () => {} };
    const result = formatConfig(obj);
    expect(result).toBe(`{
  name: 'test',
}`);
  });

  it("should ignore functions in arrays", () => {
    const arr = ["a", () => {}, "b"];
    const result = formatConfig(arr);
    expect(result).toBe(`['a','b']`);
  });

  it("should handle empty object", () => {
    const obj = {};
    const result = formatConfig(obj);
    expect(result).toBe(`{
}`);
  });

  it("should handle empty array", () => {
    const arr: unknown[] = [];
    const result = formatConfig(arr);
    expect(result).toBe(`[]`);
  });

  it("should handle mixed array with primitives and objects", () => {
    const arr = [1, "two", { key: "value" }, true];
    const result = formatConfig(arr);
    expect(result).toBe(`[1,'two',
  {
    key: 'value'
  },true]`);
  });

  it("should handle deep nested objects", () => {
    const obj = { a: { b: { c: { d: "deep" } } } };
    const result = formatConfig(obj);
    expect(result).toBe(`{
  a: {
    b: {
      c: {
        d: 'deep'
      }
    }
  }
}`);
  });

  it("should handle NaN as number (NaN is filtered out)", () => {
    const obj = { value: NaN };
    const result = formatConfig(obj);
    expect(result).toBe(`{
}`);
  });

  it("should handle boolean false", () => {
    const obj = { active: false };
    const result = formatConfig(obj);
    expect(result).toBe(`{
  active: false
}`);
  });

  it("should handle number zero", () => {
    const obj = { count: 0 };
    const result = formatConfig(obj);
    expect(result).toBe(`{
  count: 0
}`);
  });

  it("should handle number zero in array", () => {
    const arr = [0, 1, 2];
    const result = formatConfig(arr);
    expect(result).toBe(`[0,1,2]`);
  });
});