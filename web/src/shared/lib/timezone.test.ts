import { describe, it, expect } from 'vitest';
import { utcToZonedDate, zonedDateToUtcIso } from './timezone';

describe('timezone helpers', () => {
  it('round-trips UTC through America/New_York wall clock', () => {
    const original = '2026-06-10T14:30:00.000Z';
    const zoned = utcToZonedDate(original, 'America/New_York');
    const back = zonedDateToUtcIso(zoned, 'America/New_York');
    expect(back).toBe(original);
  });
});
