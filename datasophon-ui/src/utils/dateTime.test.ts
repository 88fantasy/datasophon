import { describe, it, expect, vi } from 'vitest';
import {
  formatMomentObj2YYYYMMDDHHMMSS,
  formatMomentObj2StartYYYYMMDDHHMMSS,
  formatMomentObj2EndYYYYMMDDHHMMSS,
  formatMomentObJ2YYYYMMDD,
  formatMomentObJ2HHMMSS,
  formatMomentObJ2HHMM,
  formatMomentObJ2YYYYMMDDHHMM,
  getTimeRender,
} from './dateTime';
import dayjs from 'dayjs';

describe('dateTime utils', () => {
  // Mock console.warn to avoid output during tests
  beforeEach(() => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('formatMomentObj2YYYYMMDDHHMMSS', () => {
    it('should return empty string for falsy input', () => {
      expect(formatMomentObj2YYYYMMDDHHMMSS(null)).toBe('');
      expect(formatMomentObj2YYYYMMDDHHMMSS(undefined)).toBe('');
      expect(formatMomentObj2YYYYMMDDHHMMSS(false)).toBe('');
      expect(formatMomentObj2YYYYMMDDHHMMSS(0)).toBe('');
    });

    it('should format valid Dayjs object', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObj2YYYYMMDDHHMMSS(date)).toBe('2023-01-01 12:30:45');
    });

    it('should convert valid string to Dayjs and format', () => {
      expect(formatMomentObj2YYYYMMDDHHMMSS('2023-01-01 12:30:45')).toBe('2023-01-01 12:30:45');
    });

    it('should handle invalid input and return "Invalid Date" without warning', () => {
      const consoleSpy = vi.spyOn(console, 'warn');
      expect(formatMomentObj2YYYYMMDDHHMMSS('invalid-date')).toBe('Invalid Date');
      expect(consoleSpy).not.toHaveBeenCalled();
    });

    it('should handle null/undefined in conversion attempt', () => {
      expect(formatMomentObj2YYYYMMDDHHMMSS(null as any)).toBe('');
      expect(formatMomentObj2YYYYMMDDHHMMSS(undefined as any)).toBe('');
    });
  });

  describe('formatMomentObj2StartYYYYMMDDHHMMSS', () => {
    it('should return empty string for falsy input', () => {
      expect(formatMomentObj2StartYYYYMMDDHHMMSS(null)).toBe('');
      expect(formatMomentObj2StartYYYYMMDDHHMMSS(undefined)).toBe('');
    });

    it('should format valid Dayjs object with default params', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObj2StartYYYYMMDDHHMMSS(date)).toBe('2023-01-01 00:00:00');
    });

    it('should format valid Dayjs object with startOf=true', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObj2StartYYYYMMDDHHMMSS(date, false, true)).toBe('2023-01-01 12:30:45');
    });

    it('should convert valid string to Dayjs and format with autoGenerate', () => {
      expect(formatMomentObj2StartYYYYMMDDHHMMSS('2023-01-01 12:30:45', true)).toBe('2023-01-01 00:00:00');
    });

    it('should handle invalid input without autoGenerate', () => {
      expect(formatMomentObj2StartYYYYMMDDHHMMSS('invalid-date', false)).toBe('');
    });

    it('should handle invalid input with autoGenerate and return "Invalid Date" without warning', () => {
      const consoleSpy = vi.spyOn(console, 'warn');
      expect(formatMomentObj2StartYYYYMMDDHHMMSS('invalid-date', true)).toBe('Invalid Date');
      expect(consoleSpy).not.toHaveBeenCalled();
    });
  });

  describe('formatMomentObj2EndYYYYMMDDHHMMSS', () => {
    it('should return empty string for falsy input', () => {
      expect(formatMomentObj2EndYYYYMMDDHHMMSS(null)).toBe('');
      expect(formatMomentObj2EndYYYYMMDDHHMMSS(undefined)).toBe('');
    });

    it('should format valid Dayjs object with default params', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObj2EndYYYYMMDDHHMMSS(date)).toBe('2023-01-01 23:59:59');
    });

    it('should format valid Dayjs object with endOf=true', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObj2EndYYYYMMDDHHMMSS(date, false, true)).toBe('2023-01-01 12:30:45');
    });

    it('should convert valid string to Dayjs and format with autoGenerate', () => {
      expect(formatMomentObj2EndYYYYMMDDHHMMSS('2023-01-01 12:30:45', true)).toBe('2023-01-01 23:59:59');
    });

    it('should handle invalid input without autoGenerate', () => {
      expect(formatMomentObj2EndYYYYMMDDHHMMSS('invalid-date', false)).toBe('');
    });

    it('should handle invalid input with autoGenerate and return "Invalid Date" without warning', () => {
      const consoleSpy = vi.spyOn(console, 'warn');
      expect(formatMomentObj2EndYYYYMMDDHHMMSS('invalid-date', true)).toBe('Invalid Date');
      expect(consoleSpy).not.toHaveBeenCalled();
    });
  });

  describe('formatMomentObJ2YYYYMMDD', () => {
    it('should return empty string for falsy input', () => {
      expect(formatMomentObJ2YYYYMMDD(null)).toBe('');
      expect(formatMomentObJ2YYYYMMDD(undefined)).toBe('');
    });

    it('should format valid Dayjs object', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObJ2YYYYMMDD(date)).toBe('2023-01-01');
    });

    it('should convert valid string to Dayjs and format', () => {
      expect(formatMomentObJ2YYYYMMDD('2023-01-01 12:30:45')).toBe('2023-01-01');
    });

    it('should handle invalid input and return "Invalid Date" without warning', () => {
      const consoleSpy = vi.spyOn(console, 'warn');
      expect(formatMomentObJ2YYYYMMDD('invalid-date')).toBe('Invalid Date');
      expect(consoleSpy).not.toHaveBeenCalled();
    });
  });

  describe('formatMomentObJ2HHMMSS', () => {
    it('should return empty string for falsy input', () => {
      expect(formatMomentObJ2HHMMSS(null)).toBe('');
      expect(formatMomentObJ2HHMMSS(undefined)).toBe('');
    });

    it('should format valid Dayjs object', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObJ2HHMMSS(date)).toBe('12:30:45');
    });

    it('should convert valid string to Dayjs and format', () => {
      expect(formatMomentObJ2HHMMSS('2023-01-01 12:30:45')).toBe('12:30:45');
    });

    it('should handle invalid input and return "Invalid Date" without warning', () => {
      const consoleSpy = vi.spyOn(console, 'warn');
      expect(formatMomentObJ2HHMMSS('invalid-date')).toBe('Invalid Date');
      expect(consoleSpy).not.toHaveBeenCalled();
    });
  });

  describe('formatMomentObJ2HHMM', () => {
    it('should return empty string for falsy input', () => {
      expect(formatMomentObJ2HHMM(null)).toBe('');
      expect(formatMomentObJ2HHMM(undefined)).toBe('');
    });

    it('should format valid Dayjs object', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObJ2HHMM(date)).toBe('12:30');
    });

    it('should convert valid string to Dayjs and format', () => {
      expect(formatMomentObJ2HHMM('2023-01-01 12:30:45')).toBe('12:30');
    });

    it('should handle invalid input and return "Invalid Date" without warning', () => {
      const consoleSpy = vi.spyOn(console, 'warn');
      expect(formatMomentObJ2HHMM('invalid-date')).toBe('Invalid Date');
      expect(consoleSpy).not.toHaveBeenCalled();
    });
  });

  describe('formatMomentObJ2YYYYMMDDHHMM', () => {
    it('should return empty string for falsy input', () => {
      expect(formatMomentObJ2YYYYMMDDHHMM(null)).toBe('');
      expect(formatMomentObJ2YYYYMMDDHHMM(undefined)).toBe('');
    });

    it('should format valid Dayjs object', () => {
      const date = dayjs('2023-01-01 12:30:45');
      expect(formatMomentObJ2YYYYMMDDHHMM(date)).toBe('2023-01-01 12:30');
    });

    it('should convert valid string to Dayjs and format', () => {
      expect(formatMomentObJ2YYYYMMDDHHMM('2023-01-01 12:30:45')).toBe('2023-01-01 12:30');
    });

    it('should handle invalid input and return "Invalid Date" without warning', () => {
      const consoleSpy = vi.spyOn(console, 'warn');
      expect(formatMomentObJ2YYYYMMDDHHMM('invalid-date')).toBe('Invalid Date');
      expect(consoleSpy).not.toHaveBeenCalled();
    });
  });

  describe('getTimeRender', () => {
    it('should return "——" for falsy input', () => {
      expect(getTimeRender(null)).toBe('——');
      expect(getTimeRender(undefined)).toBe('——');
      expect(getTimeRender(0)).toBe('——');
      expect(getTimeRender(false as any)).toBe('——');
    });

    it('should return seconds for values between 1 and 60', () => {
      expect(getTimeRender(1)).toBe('1秒');
      expect(getTimeRender(30)).toBe('30秒');
      expect(getTimeRender(60)).toBe('60秒');
    });

    it('should return minutes and seconds for values above 60', () => {
      expect(getTimeRender(61)).toBe('1分钟1秒');
      expect(getTimeRender(120)).toBe('2分钟0秒');
      expect(getTimeRender(3661)).toBe('61分钟1秒'); // 3661 seconds = 61 minutes 1 second
    });
  });
});