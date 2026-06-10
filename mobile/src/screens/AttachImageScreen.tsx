import React, { useCallback, useState } from 'react';
import {
  Alert,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import NetInfo from '@react-native-community/netinfo';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';

import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';
import { enqueuePhoto } from '../upload/photoQueue';
import { uploadPhoto } from '../upload/uploadPhoto';
import { usePhotoUploadQueue } from '../upload/usePhotoUploadQueue';
import UploadTray, { type UploadTrayItem } from '../components/UploadTray';

type NavProp = NativeStackNavigationProp<RootStackParamList, 'AttachImage'>;
type RouteP = RouteProp<RootStackParamList, 'AttachImage'>;

interface SelectedPhoto {
  uri: string;
  filename: string;
}

export default function AttachImageScreen() {
  const navigation = useNavigation<NavProp>();
  const route = useRoute<RouteP>();
  const jobId: string = route.params?.jobId ?? '';

  const [selectedPhotos, setSelectedPhotos] = useState<SelectedPhoto[]>([]);
  const [trayItems, setTrayItems] = useState<UploadTrayItem[]>([]);

  // Process offline queue on reconnect
  usePhotoUploadQueue(jobId);

  const updateTrayItem = useCallback((id: string, patch: Partial<UploadTrayItem>) => {
    setTrayItems((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }, []);

  const processPhoto = useCallback(
    async (asset: ImagePicker.ImagePickerAsset) => {
      const filename = asset.fileName ?? `photo-${Date.now()}.jpg`;
      const contentType = asset.mimeType ?? 'image/jpeg';
      const fileExtension = filename.split('.').pop() ?? 'jpg';
      const sizeBytes = asset.fileSize ?? 0;
      const trayId = `tray-${Date.now()}-${Math.random().toString(36).slice(2)}`;

      setSelectedPhotos((prev) => [...prev, { uri: asset.uri, filename }]);
      setTrayItems((prev) => [
        ...prev,
        { id: trayId, filename, status: 'uploading', progress: 0 },
      ]);

      const netState = await NetInfo.fetch();
      if (!netState.isConnected) {
        enqueuePhoto(jobId, asset.uri, contentType, fileExtension, sizeBytes);
        updateTrayItem(trayId, { status: 'pending' });
        return;
      }

      updateTrayItem(trayId, { status: 'uploading', progress: 10 });
      try {
        await uploadPhoto(jobId, asset.uri, contentType, fileExtension, sizeBytes);
        updateTrayItem(trayId, { status: 'done', progress: 100 });
      } catch {
        enqueuePhoto(jobId, asset.uri, contentType, fileExtension, sizeBytes);
        updateTrayItem(trayId, { status: 'error' });
      }
    },
    [jobId, updateTrayItem],
  );

  const handleTakePhoto = useCallback(async () => {
    const { status } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission needed', 'Camera access is required to take photos.');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 1,
      allowsEditing: false,
    });
    if (result.canceled || !result.assets?.length) return;
    for (const asset of result.assets) {
      await processPhoto(asset);
    }
  }, [processPhoto]);

  const handleChooseFromLibrary = useCallback(async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission needed', 'Photo library access is required.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 1,
      allowsEditing: false,
      allowsMultipleSelection: true,
    });
    if (result.canceled || !result.assets?.length) return;
    for (const asset of result.assets) {
      await processPhoto(asset);
    }
  }, [processPhoto]);

  const doneCount = trayItems.filter((i) => i.status === 'done').length;
  const pendingCount = trayItems.filter((i) => i.status === 'uploading' || i.status === 'pending').length;

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Cancel"
          style={styles.headerBtn}
        >
          <Text style={styles.headerBtnText}>Cancel</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Attach Photos</Text>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Done"
          style={styles.headerBtn}
          testID="btn-done"
        >
          <Text style={[styles.headerBtnText, styles.headerBtnBold]}>Done</Text>
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
        {/* Action buttons */}
        <View style={styles.actionRow}>
          <TouchableOpacity
            style={styles.pickBtn}
            onPress={handleTakePhoto}
            accessibilityRole="button"
            accessibilityLabel="Take Photo"
            testID="btn-take-photo"
          >
            <Text style={styles.pickIcon}>📷</Text>
            <Text style={styles.pickLabel}>Take Photo</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.pickBtn}
            onPress={handleChooseFromLibrary}
            accessibilityRole="button"
            accessibilityLabel="Choose from Library"
            testID="btn-choose-library"
          >
            <Text style={styles.pickIcon}>🖼️</Text>
            <Text style={styles.pickLabel}>Choose from Library</Text>
          </TouchableOpacity>
        </View>

        {/* Selected thumbnails */}
        {selectedPhotos.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>SELECTED PHOTOS</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.thumbRow}>
              {selectedPhotos.map((p, idx) => (
                <Image
                  key={`${p.uri}-${idx}`}
                  source={{ uri: p.uri }}
                  style={styles.thumb}
                  accessibilityLabel={p.filename}
                />
              ))}
            </ScrollView>
          </View>
        )}

        {/* Upload tray */}
        {trayItems.length > 0 && (
          <View style={styles.section}>
            <UploadTray items={trayItems} />
            {pendingCount > 0 && (
              <Text style={styles.offlineHint}>
                {pendingCount} photo{pendingCount > 1 ? 's' : ''} queued — will upload when back online.
              </Text>
            )}
            {doneCount > 0 && pendingCount === 0 && (
              <Text style={styles.doneHint}>All photos uploaded successfully.</Text>
            )}
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingTop: 56,
    paddingHorizontal: 16,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  headerBtn: {
    paddingVertical: 4,
    minWidth: 60,
  },
  headerBtnText: {
    fontSize: 16,
    color: colors.primary,
  },
  headerBtnBold: {
    fontWeight: '600',
    textAlign: 'right',
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: colors.text,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    gap: 16,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
  },
  pickBtn: {
    flex: 1,
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    gap: 6,
  },
  pickIcon: {
    fontSize: 28,
  },
  pickLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#FFFFFF',
    textAlign: 'center',
  },
  section: {
    gap: 8,
  },
  sectionLabel: {
    fontSize: 10,
    fontWeight: '700',
    color: colors.tabInactive,
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  thumbRow: {
    flexDirection: 'row',
  },
  thumb: {
    width: 80,
    height: 80,
    borderRadius: 8,
    marginRight: 8,
    backgroundColor: colors.border,
  },
  offlineHint: {
    fontSize: 12,
    color: '#F59E0B',
    fontStyle: 'italic',
  },
  doneHint: {
    fontSize: 12,
    color: '#10B981',
  },
});
