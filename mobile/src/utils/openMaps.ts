import { Linking, Platform, Clipboard } from 'react-native';

// TO-DO: replace Clipboard from 'react-native' with @react-native-clipboard/clipboard once it is added as a dependency

export interface MapDestination {
  lat?: number | null;
  lon?: number | null;
  address?: string | null;
  label?: string;
}

export async function openMaps({ lat, lon, address, label = 'Destination' }: MapDestination) {
  if (Platform.OS === 'android') {
    const query =
      lat != null && lon != null
        ? `geo:0,0?q=${lat},${lon}(${encodeURIComponent(label)})`
        : `geo:0,0?q=${encodeURIComponent(address ?? '')}`;
    await Linking.openURL(query);
    return;
  }

  // iOS: prefer Google Maps if installed and lat/lon is available
  if (lat != null && lon != null) {
    const googleUrl = `comgooglemaps://?daddr=${lat},${lon}&directionsmode=driving`;
    const canGoogle = await Linking.canOpenURL(googleUrl);
    if (canGoogle) {
      await Linking.openURL(googleUrl);
      return;
    }
  }

  // iOS: Apple Maps — coordinate-based or address-based fallback
  const dest =
    lat != null && lon != null
      ? `${lat},${lon}`
      : encodeURIComponent(address ?? '');
  await Linking.openURL(`maps://?daddr=${dest}`);
}

export function copyAddress(address: string) {
  Clipboard.setString(address);
}
