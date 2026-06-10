import { jest, describe, it, expect, beforeEach } from '@jest/globals';
import * as FileSystem from 'expo-file-system/legacy';
import { apiClient } from '../api/client';
import { uploadPhoto } from '../upload/uploadPhoto';
import { enqueuePhoto, getPendingPhotos, initPhotoQueue } from '../upload/photoQueue';

jest.mock('expo-file-system/legacy', () => ({
  uploadAsync: jest.fn<() => Promise<{ headers: { etag: string } }>>().mockResolvedValue({ headers: { etag: '"abc123"' } }),
  getInfoAsync: jest.fn<() => Promise<unknown>>().mockResolvedValue({ size: 1024 * 1024, exists: true, isDirectory: false, uri: 'file://stripped.jpg' }),
  FileSystemUploadType: { BINARY_CONTENT: 0 },
}));

jest.mock('expo-image-manipulator', () => ({
  manipulateAsync: jest.fn<() => Promise<unknown>>().mockResolvedValue({ uri: 'file://stripped.jpg', width: 1920, height: 1080 }),
  SaveFormat: { JPEG: 'JPEG' },
}));

jest.mock('../api/client', () => ({
  apiClient: {
    post: jest.fn(),
  },
}));

// expo-sqlite is globally mocked in jest.setup.js

const mockPost = apiClient.post as jest.MockedFunction<() => Promise<unknown>>;
const mockGetInfoAsync = FileSystem.getInfoAsync as jest.MockedFunction<() => Promise<unknown>>;
const mockUploadAsync = FileSystem.uploadAsync as jest.MockedFunction<() => Promise<unknown>>;

const SMALL_SIZE = 1024 * 1024; // 1 MB — below 5 MB threshold
const LARGE_SIZE = 6 * 1024 * 1024; // 6 MB — above 5 MB threshold

describe('uploadPhoto — small upload (< 5 MB)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetInfoAsync.mockResolvedValue({
      size: SMALL_SIZE,
      exists: true,
      isDirectory: false,
      uri: 'file://stripped.jpg',
    });
  });

  it('calls photo-upload-url, PUT, then photos endpoint in order', async () => {
    mockPost
      .mockResolvedValueOnce({ data: { uploadUrl: 'https://s3.example.com/presigned', objectKey: 'obj/key.jpg', expiresAt: '' } })
      .mockResolvedValueOnce({ data: {} });

    await uploadPhoto('job-1', 'file://local.jpg', 'image/jpeg', 'jpg', SMALL_SIZE);

    expect(mockPost).toHaveBeenCalledTimes(2);
    expect(mockPost).toHaveBeenNthCalledWith(1, '/api/v1/jobs/job-1/photo-upload-url', { contentType: 'image/jpeg', fileExtension: 'jpg' });
    expect(mockUploadAsync).toHaveBeenCalledWith(
      'https://s3.example.com/presigned',
      'file://stripped.jpg',
      expect.objectContaining({ httpMethod: 'PUT' }),
    );
    expect(mockPost).toHaveBeenNthCalledWith(
      2,
      '/api/v1/jobs/job-1/photos',
      expect.objectContaining({ objectKey: 'obj/key.jpg', contentType: 'image/jpeg' }),
    );
  });
});

describe('uploadPhoto — multipart upload (≥ 5 MB)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetInfoAsync.mockResolvedValue({
      size: LARGE_SIZE,
      exists: true,
      isDirectory: false,
      uri: 'file://stripped.jpg',
    });
  });

  it('calls multipart/start, part-url, PUT, then complete endpoint', async () => {
    mockPost
      .mockResolvedValueOnce({ data: { uploadId: 'upload-uuid-123', objectKey: 'obj/key.jpg' } })
      .mockResolvedValueOnce({ data: { uploadUrl: 'https://s3.example.com/part1', partNumber: 1, expiresAt: '' } })
      .mockResolvedValueOnce({ data: {} });

    await uploadPhoto('job-2', 'file://local.jpg', 'image/jpeg', 'jpg', LARGE_SIZE);

    expect(mockPost).toHaveBeenCalledTimes(3);
    expect(mockPost).toHaveBeenNthCalledWith(1, '/api/v1/jobs/job-2/photo-multipart/start', { contentType: 'image/jpeg', fileExtension: 'jpg' });
    expect(mockPost).toHaveBeenNthCalledWith(2, '/api/v1/jobs/job-2/photo-multipart/upload-uuid-123/part-url', { partNumber: 1 });
    expect(mockUploadAsync).toHaveBeenCalledWith(
      'https://s3.example.com/part1',
      'file://stripped.jpg',
      expect.objectContaining({ httpMethod: 'PUT' }),
    );
    expect(mockPost).toHaveBeenNthCalledWith(
      3,
      '/api/v1/jobs/job-2/photo-multipart/upload-uuid-123/complete',
      expect.objectContaining({ parts: [{ partNumber: 1, eTag: '"abc123"' }] }),
    );
  });
});

describe('photoQueue', () => {
  beforeEach(() => {
    initPhotoQueue();
  });

  it('enqueuePhoto stores record and returns an id', () => {
    const id = enqueuePhoto('job-3', 'file://photo.jpg', 'image/jpeg', 'jpg', 512000);
    expect(typeof id).toBe('string');
    expect(id).toMatch(/^photo-/);
  });

  it('getPendingPhotos returns only pending/error records (mocked as empty)', () => {
    const results = getPendingPhotos('job-3');
    expect(Array.isArray(results)).toBe(true);
    expect(results).toHaveLength(0);
  });
});
