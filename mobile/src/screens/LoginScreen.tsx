import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Alert,
} from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import * as AppleAuthentication from 'expo-apple-authentication';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useNavigation } from '@react-navigation/native';

import { colors } from '../theme/colors';
import { AuthStackParamList } from '../navigation/types';
import { loginWithPassword } from '../api/authApi';
import { registerPushToken } from '../api/deviceApi';
import { useAuthStore } from '../stores/authStore';

WebBrowser.maybeCompleteAuthSession();

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'https://api.teamhub.online';

type NavProp = NativeStackNavigationProp<AuthStackParamList, 'Login'>;

function validateEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export default function LoginScreen() {
  const navigation = useNavigation<NavProp>();
  const { setTokens } = useAuthStore();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);

  function validate(): boolean {
    let valid = true;
    if (!email.trim()) {
      setEmailError('Email is required');
      valid = false;
    } else if (!validateEmail(email.trim())) {
      setEmailError('Enter a valid email address');
      valid = false;
    } else {
      setEmailError('');
    }
    if (!password) {
      setPasswordError('Password is required');
      valid = false;
    } else {
      setPasswordError('');
    }
    return valid;
  }

  async function handleSignIn() {
    if (!validate()) return;
    setLoading(true);
    try {
      const tokens = await loginWithPassword(email.trim(), password);
      await setTokens(tokens.accessToken, tokens.refreshToken);
      await registerPushToken(tokens.accessToken);
    } catch {
      Alert.alert('Sign in failed', 'Invalid email or password. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  async function handleGoogleSignIn() {
    setGoogleLoading(true);
    try {
      const redirectUri = Linking.createURL('auth/callback');
      const authUrl = `${BASE_URL}/oauth2/authorization/google?redirect_uri=${encodeURIComponent(redirectUri)}`;
      const result = await WebBrowser.openAuthSessionAsync(authUrl, redirectUri);
      if (result.type === 'success') {
        const url = new URL(result.url);
        const accessToken = url.searchParams.get('access_token');
        const refreshToken = url.searchParams.get('refresh_token');
        if (accessToken && refreshToken) {
          await setTokens(accessToken, refreshToken);
          await registerPushToken(accessToken);
        } else {
          Alert.alert('Sign in failed', 'Could not complete Google sign-in. Please try again.');
        }
      }
    } catch {
      Alert.alert('Sign in failed', 'Google sign-in encountered an error. Please try again.');
    } finally {
      setGoogleLoading(false);
    }
  }

  async function handleAppleSignIn() {
    // TO-DO (TASK-022): backend needs to verify Apple identity tokens.
    // Show stub toast until TASK-022 lands.
    Alert.alert('Coming soon', 'Apple Sign-in will be available soon.');
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={styles.scroll}
        keyboardShouldPersistTaps="handled"
      >
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.logo}>Teamhub</Text>
          <Text style={styles.tagline}>Field service, simplified.</Text>
        </View>

        {/* Email */}
        <View style={styles.field}>
          <Text style={styles.label}>Email</Text>
          <TextInput
            style={[styles.input, emailError ? styles.inputError : null]}
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="you@company.com"
            placeholderTextColor={colors.tabInactive}
            testID="email-input"
          />
          {!!emailError && <Text style={styles.errorText}>{emailError}</Text>}
        </View>

        {/* Password */}
        <View style={styles.field}>
          <Text style={styles.label}>Password</Text>
          <TextInput
            style={[styles.input, passwordError ? styles.inputError : null]}
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            placeholder="••••••••"
            placeholderTextColor={colors.tabInactive}
            testID="password-input"
          />
          {!!passwordError && <Text style={styles.errorText}>{passwordError}</Text>}
        </View>

        {/* Sign in button */}
        <Pressable
          style={({ pressed }) => [
            styles.primaryButton,
            (loading || pressed) && styles.buttonPressed,
          ]}
          onPress={handleSignIn}
          disabled={loading}
          testID="sign-in-button"
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.primaryButtonText}>Sign in</Text>
          )}
        </Pressable>

        {/* Divider */}
        <View style={styles.divider}>
          <View style={styles.dividerLine} />
          <Text style={styles.dividerText}>or</Text>
          <View style={styles.dividerLine} />
        </View>

        {/* Google sign in */}
        <Pressable
          style={({ pressed }) => [
            styles.outlineButton,
            (googleLoading || pressed) && styles.buttonPressed,
          ]}
          onPress={handleGoogleSignIn}
          disabled={googleLoading}
          testID="google-sign-in-button"
        >
          {googleLoading ? (
            <ActivityIndicator color={colors.tabActive} />
          ) : (
            <Text style={styles.outlineButtonText}>Sign in with Google</Text>
          )}
        </Pressable>

        {/* Apple sign in — iOS only */}
        {Platform.OS === 'ios' && (
          <AppleAuthentication.AppleAuthenticationButton
            buttonType={AppleAuthentication.AppleAuthenticationButtonType.SIGN_IN}
            buttonStyle={AppleAuthentication.AppleAuthenticationButtonStyle.BLACK}
            cornerRadius={8}
            style={styles.appleButton}
            onPress={handleAppleSignIn}
          />
        )}

        {/* Forgot password */}
        <Pressable
          style={styles.forgotContainer}
          onPress={() => navigation.navigate('ForgotPassword')}
          testID="forgot-password-link"
        >
          <Text style={styles.forgotText}>Forgot password?</Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ffffff',
  },
  scroll: {
    flexGrow: 1,
    padding: 24,
    justifyContent: 'center',
  },
  header: {
    alignItems: 'center',
    marginBottom: 40,
  },
  logo: {
    fontSize: 32,
    fontWeight: '700',
    color: colors.tabActive,
    letterSpacing: -0.5,
  },
  tagline: {
    fontSize: 14,
    color: colors.tabInactive,
    marginTop: 4,
  },
  field: {
    marginBottom: 16,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.text,
    marginBottom: 6,
  },
  input: {
    height: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingHorizontal: 14,
    fontSize: 16,
    color: colors.text,
    backgroundColor: '#fafafa',
  },
  inputError: {
    borderColor: '#ef4444',
  },
  errorText: {
    fontSize: 12,
    color: '#ef4444',
    marginTop: 4,
  },
  primaryButton: {
    height: 48,
    backgroundColor: colors.tabActive,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
  },
  buttonPressed: {
    opacity: 0.7,
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
  divider: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 24,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: colors.border,
  },
  dividerText: {
    marginHorizontal: 12,
    color: colors.tabInactive,
    fontSize: 13,
  },
  outlineButton: {
    height: 48,
    borderWidth: 1.5,
    borderColor: colors.tabActive,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
  },
  outlineButtonText: {
    color: colors.tabActive,
    fontSize: 16,
    fontWeight: '600',
  },
  appleButton: {
    height: 48,
    marginBottom: 12,
  },
  forgotContainer: {
    alignItems: 'center',
    marginTop: 16,
  },
  forgotText: {
    color: colors.tabActive,
    fontSize: 14,
  },
});
