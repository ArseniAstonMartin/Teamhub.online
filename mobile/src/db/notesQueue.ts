import * as SQLite from 'expo-sqlite';

let _db: ReturnType<typeof SQLite.openDatabaseSync> | null = null;

function getDb() {
  if (!_db) {
    _db = SQLite.openDatabaseSync('teamhub.db');
  }
  return _db;
}

export function initNotesQueue() {
  getDb().execSync(`
    CREATE TABLE IF NOT EXISTS offline_notes (
      id TEXT PRIMARY KEY,
      job_id TEXT NOT NULL,
      body TEXT NOT NULL,
      created_at TEXT NOT NULL,
      synced INTEGER NOT NULL DEFAULT 0
    )
  `);
}

export function enqueueNote(jobId: string, body: string): string {
  const id = `local-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const createdAt = new Date().toISOString();
  getDb().runSync(
    'INSERT INTO offline_notes (id, job_id, body, created_at, synced) VALUES (?, ?, ?, ?, 0)',
    [id, jobId, body, createdAt],
  );
  return id;
}

export function getPendingNotes(jobId: string) {
  return getDb().getAllSync<{ id: string; job_id: string; body: string; created_at: string }>(
    'SELECT * FROM offline_notes WHERE job_id = ? AND synced = 0 ORDER BY created_at ASC',
    [jobId],
  );
}

export function markNoteSynced(localId: string) {
  getDb().runSync('UPDATE offline_notes SET synced = 1 WHERE id = ?', [localId]);
}

export function deleteNote(localId: string) {
  getDb().runSync('DELETE FROM offline_notes WHERE id = ?', [localId]);
}
