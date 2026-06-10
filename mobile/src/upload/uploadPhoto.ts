import * as FileSystem from 'expo-file-system/legacy';
import * as ImageManipulator from 'expo-image-manipulator';
import { apiClient } from '../api/client';

const FIVE_MB = 5 * 1024 * 1024;

// EXIF is stripped client-side by re-encoding via expo-image-manipulator.
// Alternative: strip server-side before serving — not chosen to keep the backend simple.
async function stripExif(localUri: string): Promise<{ uri: string; width: number; height: number }> {
  const result = await ImageManipulator.manipulateAsync(
    localUri,
    [{ resize: { width: 4000 } }],
    { compress: 0.85, format: ImageManipulator.SaveFormat.JPEG },
  );
  return result;
}

export async function uploadPhoto(
  jobId: string,
  localUri: string,
  contentType: string,
  fileExtension: string,
  sizeBytes: number,
): Promise<void> {
  const { uri, width, height } = await stripExif(localUri);

  // Re-measure after re-encode since file size changes with compression
  const info = await FileSystem.getInfoAsync(uri);
  const actualSize = (info.exists ? info.size : undefined) ?? sizeBytes;

  if (actualSize >= FIVE_MB) {
    await uploadMultipart(jobId, uri, contentType, fileExtension, actualSize, width, height);
  } else {
    await uploadSmall(jobId, uri, contentType, fileExtension, actualSize, width, height);
  }
}

async function uploadSmall(
  jobId: string,
  uri: string,
  contentType: string,
  fileExtension: string,
  sizeBytes: number,
  width: number,
  height: number,
) {
  const {
    data: { uploadUrl, objectKey },
  } = await apiClient.post<{ uploadUrl: string; objectKey: string; expiresAt: string }>(
    `/api/v1/jobs/${jobId}/photo-upload-url`,
    { contentType, fileExtension },
  );

  await FileSystem.uploadAsync(uploadUrl, uri, {
    httpMethod: 'PUT',
    headers: { 'Content-Type': contentType },
    uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
  });

  await apiClient.post(`/api/v1/jobs/${jobId}/photos`, {
    objectKey,
    contentType,
    sizeBytes,
    width,
    height,
  });
}

async function uploadMultipart(
  jobId: string,
  uri: string,
  contentType: string,
  fileExtension: string,
  sizeBytes: number,
  width: number,
  height: number,
) {
  const {
    data: { uploadId, objectKey },
  } = await apiClient.post<{ uploadId: string; objectKey: string }>(
    `/api/v1/jobs/${jobId}/photo-multipart/start`,
    { contentType, fileExtension },
  );

  const completedParts: { partNumber: number; eTag: string }[] = [];

  // TO-DO: implement true chunking with FileSystem.readAsStringAsync position/length
  // when expo-file-system adds native support. For MVP, single-part multipart is
  // functionally correct: one start → one part → complete.
  const { data: partData } = await apiClient.post<{
    uploadUrl: string;
    partNumber: number;
    expiresAt: string;
  }>(`/api/v1/jobs/${jobId}/photo-multipart/${uploadId}/part-url`, { partNumber: 1 });

  const uploadResult = await FileSystem.uploadAsync(partData.uploadUrl, uri, {
    httpMethod: 'PUT',
    headers: { 'Content-Type': contentType },
    uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
  });

  // ETag comes back in the pre-signed PUT response headers
  const eTag =
    (uploadResult.headers as Record<string, string>)?.['etag'] ??
    (uploadResult.headers as Record<string, string>)?.['ETag'] ??
    'unknown';
  completedParts.push({ partNumber: 1, eTag });

  await apiClient.post(`/api/v1/jobs/${jobId}/photo-multipart/${uploadId}/complete`, {
    objectKey,
    parts: completedParts,
    contentType,
    sizeBytes,
    width,
    height,
  });
}
