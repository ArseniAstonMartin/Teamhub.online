import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import * as Sentry from '@sentry/react-native';
import Constants from 'expo-constants';

// TO-DO: human must provision Sentry projects per PRD §26.2 before enabling in production.
// DSN is read from EXPO_PUBLIC_SENTRY_DSN — no-op when absent (local dev without Sentry).
const _appVersion = Constants.expoConfig?.version ?? '1.0.0';
// EAS Build sets EAS_BUILD_GIT_COMMIT_HASH automatically; falls back to 'local' in dev.
const _gitSha = (process.env.EAS_BUILD_GIT_COMMIT_HASH ?? 'local').slice(0, 7);

Sentry.init({
  dsn: process.env.EXPO_PUBLIC_SENTRY_DSN,
  release: `teamhub-mobile@${_appVersion}+${_gitSha}`,
  environment: __DEV__ ? 'development' : 'production',
  sendDefaultPii: false,
  enableLogs: false,
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1,
  integrations: [Sentry.mobileReplayIntegration(), Sentry.feedbackIntegration()],
});

export default Sentry.wrap(function App() {
  return (
    <View style={styles.container}>
      <Text style={styles.logo}>Teamhub</Text>
      <Text style={styles.tagline}>Field Service Management</Text>
      <StatusBar style="light" />
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  logo: {
    fontSize: 36,
    fontWeight: '700',
    color: '#FFFFFF',
    letterSpacing: -0.5,
  },
  tagline: {
    fontSize: 14,
    fontWeight: '400',
    color: '#94A3B8',
    marginTop: 8,
  },
});
