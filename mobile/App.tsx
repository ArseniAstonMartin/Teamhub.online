import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';

export default function App(): React.JSX.Element {
  return (
    <View style={styles.container}>
      <Text style={styles.logo}>Teamhub</Text>
      <Text style={styles.tagline}>Field Service Management</Text>
      <StatusBar style="light" />
    </View>
  );
}

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
