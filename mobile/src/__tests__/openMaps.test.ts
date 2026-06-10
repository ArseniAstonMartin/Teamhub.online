import { Linking, Platform, Clipboard } from 'react-native';
import { openMaps, copyAddress } from '../utils/openMaps';

const canOpenURLSpy = jest.spyOn(Linking, 'canOpenURL');
const openURLSpy = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined as unknown as boolean);
const clipboardSpy = jest.spyOn(Clipboard, 'setString').mockImplementation(() => {});

beforeEach(() => {
  jest.clearAllMocks();
  canOpenURLSpy.mockResolvedValue(false);
  openURLSpy.mockResolvedValue(undefined as unknown as boolean);
});

describe('openMaps — Android', () => {
  beforeAll(() => {
    Object.defineProperty(Platform, 'OS', { get: () => 'android' });
  });

  it('opens geo: scheme with lat/lon when both are available', async () => {
    await openMaps({ lat: 45.52, lon: -122.68, label: 'Job site' });
    expect(openURLSpy).toHaveBeenCalledWith(
      `geo:0,0?q=45.52,-122.68(${encodeURIComponent('Job site')})`,
    );
  });

  it('falls back to geo: with encoded address when lat/lon are null', async () => {
    await openMaps({ lat: null, lon: null, address: '123 Main St, Portland' });
    expect(openURLSpy).toHaveBeenCalledWith(
      `geo:0,0?q=${encodeURIComponent('123 Main St, Portland')}`,
    );
  });
});

describe('openMaps — iOS', () => {
  beforeAll(() => {
    Object.defineProperty(Platform, 'OS', { get: () => 'ios' });
  });

  it('uses Google Maps URL when canOpenURL returns true', async () => {
    canOpenURLSpy.mockResolvedValue(true);
    await openMaps({ lat: 45.52, lon: -122.68, label: 'Job site' });
    expect(openURLSpy).toHaveBeenCalledWith(
      'comgooglemaps://?daddr=45.52,-122.68&directionsmode=driving',
    );
  });

  it('falls back to Apple Maps when Google Maps is not installed', async () => {
    canOpenURLSpy.mockResolvedValue(false);
    await openMaps({ lat: 45.52, lon: -122.68, label: 'Job site' });
    expect(openURLSpy).toHaveBeenCalledWith('maps://?daddr=45.52,-122.68');
  });

  it('uses encoded address string in Apple Maps URL when lat/lon are null', async () => {
    canOpenURLSpy.mockResolvedValue(false);
    await openMaps({ lat: null, lon: null, address: '123 Main St, Portland' });
    expect(openURLSpy).toHaveBeenCalledWith(
      `maps://?daddr=${encodeURIComponent('123 Main St, Portland')}`,
    );
  });
});

describe('copyAddress', () => {
  it('calls Clipboard.setString with the address', () => {
    copyAddress('456 Oak Ave, Seattle');
    expect(clipboardSpy).toHaveBeenCalledWith('456 Oak Ave, Seattle');
  });
});
