import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import gobalEvent, { uiEvent } from './gobalEvent';
import EventEmitter from 'eventemitter3';

describe('gobalEvent', () => {
  beforeEach(() => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    gobalEvent.removeAllListeners();
  });

  it('should be an EventEmitter instance', () => {
    expect(gobalEvent).toBeInstanceOf(EventEmitter);
  });

  it('should have EventEmitter methods', () => {
    expect(typeof gobalEvent.emit).toBe('function');
    expect(typeof gobalEvent.on).toBe('function');
    expect(typeof gobalEvent.off).toBe('function');
    expect(typeof gobalEvent.removeListener).toBe('function');
    expect(typeof gobalEvent.removeAllListeners).toBe('function');
    expect(typeof gobalEvent.once).toBe('function');
  });

  describe('emit and on', () => {
    it('should emit and receive events', () => {
      const mockFn = vi.fn();
      gobalEvent.on('test-event', mockFn);
      gobalEvent.emit('test-event', { data: 'test' });
      expect(mockFn).toHaveBeenCalledTimes(1);
      expect(mockFn).toHaveBeenCalledWith({ data: 'test' });
    });

    it('should emit events with multiple listeners', () => {
      const mockFn1 = vi.fn();
      const mockFn2 = vi.fn();
      gobalEvent.on('test-event', mockFn1);
      gobalEvent.on('test-event', mockFn2);
      gobalEvent.emit('test-event');
      expect(mockFn1).toHaveBeenCalledTimes(1);
      expect(mockFn2).toHaveBeenCalledTimes(1);
    });

    it('should remove specific listener', () => {
      const mockFn = vi.fn();
      gobalEvent.on('test-event', mockFn);
      gobalEvent.removeListener('test-event', mockFn);
      gobalEvent.emit('test-event');
      expect(mockFn).not.toHaveBeenCalled();
    });

    it('should remove all listeners for an event', () => {
      const mockFn1 = vi.fn();
      const mockFn2 = vi.fn();
      gobalEvent.on('test-event', mockFn1);
      gobalEvent.on('test-event', mockFn2);
      gobalEvent.removeAllListeners('test-event');
      gobalEvent.emit('test-event');
      expect(mockFn1).not.toHaveBeenCalled();
      expect(mockFn2).not.toHaveBeenCalled();
    });
  });

  describe('once', () => {
    it('should only fire once', () => {
      const mockFn = vi.fn();
      gobalEvent.once('once-event', mockFn);
      gobalEvent.emit('once-event');
      gobalEvent.emit('once-event');
      expect(mockFn).toHaveBeenCalledTimes(1);
    });
  });
});

describe('uiEvent', () => {
  beforeEach(() => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should be an object', () => {
    expect(uiEvent).toBeDefined();
    expect(typeof uiEvent).toBe('object');
  });

  it('should have updateDataProcessingDagNodeSize property', () => {
    expect(uiEvent).toHaveProperty('updateDataProcessingDagNodeSize');
    expect(typeof uiEvent.updateDataProcessingDagNodeSize).toBe('string');
    expect(uiEvent.updateDataProcessingDagNodeSize).toMatch(/^elid-\d+$/);
  });

  it('should have updateDataProcessingDagNodeData property', () => {
    expect(uiEvent).toHaveProperty('updateDataProcessingDagNodeData');
    expect(typeof uiEvent.updateDataProcessingDagNodeData).toBe('string');
    expect(uiEvent.updateDataProcessingDagNodeData).toMatch(/^elid-\d+$/);
  });

  it('should have updateServiceInstanceList property', () => {
    expect(uiEvent).toHaveProperty('updateServiceInstanceList');
    expect(typeof uiEvent.updateServiceInstanceList).toBe('string');
    expect(uiEvent.updateServiceInstanceList).toMatch(/^elid-\d+$/);
  });

  it('should have unique values', () => {
    const values = [
      uiEvent.updateDataProcessingDagNodeSize,
      uiEvent.updateDataProcessingDagNodeData,
      uiEvent.updateServiceInstanceList,
    ];
    const uniqueValues = new Set(values);
    expect(uniqueValues.size).toBe(values.length);
  });
});