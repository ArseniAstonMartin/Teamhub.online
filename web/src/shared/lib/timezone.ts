/** Converts a UTC instant to a Date whose local getters reflect wall-clock time in `timeZone`. */
export function utcToZonedDate(utcIso: string, timeZone: string): Date {
  const utc = new Date(utcIso);
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(utc);

  const get = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((p) => p.type === type)?.value ?? '0');

  return new Date(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'));
}

/** Converts a wall-clock Date (as shown in `timeZone`) back to a UTC ISO string. */
export function zonedDateToUtcIso(localDate: Date, timeZone: string): string {
  const guessUtc = Date.UTC(
    localDate.getFullYear(),
    localDate.getMonth(),
    localDate.getDate(),
    localDate.getHours(),
    localDate.getMinutes(),
    localDate.getSeconds(),
  );

  const offsetMs = getTimeZoneOffsetMs(new Date(guessUtc), timeZone);
  return new Date(guessUtc - offsetMs).toISOString();
}

function getTimeZoneOffsetMs(date: Date, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date);

  const get = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((p) => p.type === type)?.value ?? '0');

  const asUtc = Date.UTC(
    get('year'),
    get('month') - 1,
    get('day'),
    get('hour'),
    get('minute'),
    get('second'),
  );

  return asUtc - date.getTime();
}
