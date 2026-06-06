import { enqueueNote, getPendingNotes, markNoteSynced } from '../db/notesQueue';

// expo-sqlite is mocked in jest.setup.js. Grab the mock db object from the factory.
const mockDb = {
  execSync: jest.fn(),
  runSync: jest.fn(),
  getAllSync: jest.fn().mockReturnValue([]),
};

jest.mock('expo-sqlite', () => ({
  openDatabaseSync: () => mockDb,
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockDb.getAllSync.mockReturnValue([]);
});

describe('notesQueue', () => {
  it('enqueueNote stores a note with synced=0', () => {
    const id = enqueueNote('job-42', 'Replaced the valve');
    expect(id).toMatch(/^local-/);
    expect(mockDb.runSync).toHaveBeenCalledWith(
      'INSERT INTO offline_notes (id, job_id, body, created_at, synced) VALUES (?, ?, ?, ?, 0)',
      expect.arrayContaining(['job-42', 'Replaced the valve']),
    );
  });

  it('getPendingNotes returns only unsynced notes for given jobId', () => {
    const rows = [
      { id: 'local-1', job_id: 'job-42', body: 'Note 1', created_at: '2026-01-01T00:00:00Z' },
    ];
    mockDb.getAllSync.mockReturnValueOnce(rows);
    const result = getPendingNotes('job-42');
    expect(mockDb.getAllSync).toHaveBeenCalledWith(
      'SELECT * FROM offline_notes WHERE job_id = ? AND synced = 0 ORDER BY created_at ASC',
      ['job-42'],
    );
    expect(result).toEqual(rows);
  });

  it('markNoteSynced sets synced=1 for the given id', () => {
    markNoteSynced('local-abc');
    expect(mockDb.runSync).toHaveBeenCalledWith(
      'UPDATE offline_notes SET synced = 1 WHERE id = ?',
      ['local-abc'],
    );
  });
});
