import NetInfo from '@react-native-community/netinfo';
import { useEffect } from 'react';
import { deletePendingPhoto, getPendingPhotos, setPhotoStatus } from './photoQueue';
import { uploadPhoto } from './uploadPhoto';

export function usePhotoUploadQueue(jobId: string) {
  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener(async (state) => {
      if (!state.isConnected) return;
      const pending = getPendingPhotos(jobId);
      for (const photo of pending) {
        setPhotoStatus(photo.id, 'uploading');
        try {
          await uploadPhoto(
            photo.jobId,
            photo.localUri,
            photo.contentType,
            photo.fileExtension,
            photo.sizeBytes,
          );
          deletePendingPhoto(photo.id);
        } catch {
          setPhotoStatus(photo.id, 'error');
        }
      }
    });
    return () => unsubscribe();
  }, [jobId]);
}
