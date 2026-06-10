import * as SQLite from 'expo-sqlite';

let _db: ReturnType<typeof SQLite.openDatabaseSync> | null = null;

function getDb() {
  if (!_db) {
    _db = SQLite.openDatabaseSync('teamhub.db');
  }
  return _db;
}

export interface PendingPhoto {
  id: string;
  jobId: string;
  localUri: string;
  contentType: string;
  fileExtension: string;
  sizeBytes: number;
  status: 'pending' | 'uploading' | 'done' | 'error';
  createdAt: string;
}

export function initPhotoQueue() {
  getDb().execSync(`
    CREATE TABLE IF NOT EXISTS pending_photos (
      id TEXT PRIMARY KEY,
      job_id TEXT NOT NULL,
      local_uri TEXT NOT NULL,
      content_type TEXT NOT NULL,
      file_extension TEXT NOT NULL,
      size_bytes INTEGER NOT NULL,
      status TEXT NOT NULL DEFAULT 'pending',
      created_at TEXT NOT NULL
    )
  `);
}

export function enqueuePhoto(
  jobId: string,
  localUri: string,
  contentType: string,
  fileExtension: string,
  sizeBytes: number,
): string {
  const id = `photo-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  getDb().runSync('INSERT INTO pending_photos VALUES (?, ?, ?, ?, ?, ?, ?, ?)', [
    id,
    jobId,
    localUri,
    contentType,
    fileExtension,
    sizeBytes,
    'pending',
    new Date().toISOString(),
  ]);
  return id;
}

export function getPendingPhotos(jobId: string): PendingPhoto[] {
  return getDb().getAllSync<{
    id: string;
    job_id: string;
    local_uri: string;
    content_type: string;
    file_extension: string;
    size_bytes: number;
    status: string;
    created_at: string;
  }>(
    "SELECT * FROM pending_photos WHERE job_id = ? AND status IN ('pending','error') ORDER BY created_at ASC",
    [jobId],
  ).map((row) => ({
    id: row.id,
    jobId: row.job_id,
    localUri: row.local_uri,
    contentType: row.content_type,
    fileExtension: row.file_extension,
    sizeBytes: row.size_bytes,
    status: row.status as PendingPhoto['status'],
    createdAt: row.created_at,
  }));
}

export function setPhotoStatus(id: string, status: PendingPhoto['status']) {
  getDb().runSync('UPDATE pending_photos SET status = ? WHERE id = ?', [status, id]);
}

export function deletePendingPhoto(id: string) {
  getDb().runSync('DELETE FROM pending_photos WHERE id = ?', [id]);
}
