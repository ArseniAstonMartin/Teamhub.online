# Teamhub Mobile

React Native + Expo SDK mobile application for the Teamhub field-service management platform.

## Tech Stack

- **Expo SDK 56** (managed workflow)
- **React Native 0.79**
- **TypeScript** (strict mode)
- **React Navigation 7** — screen navigation
- **TanStack Query 5** — server state / data fetching
- **Zustand 5** — client-side state management
- **Expo SecureStore** — encrypted credential storage
- **Expo Notifications** — push notifications
- **Expo ImagePicker / Camera** — photo capture for job sites

## Folder Structure (Feature-Sliced Design)

```
mobile/
├── App.tsx                  # Root component
├── app.json                 # Expo project config
├── eas.json                 # EAS Build profiles
├── src/
│   ├── screens/             # Top-level screen components
│   ├── navigation/          # React Navigation stacks & tabs
│   ├── features/            # Feature slices (jobs, customers, invoices …)
│   ├── entities/            # Domain entities (Job, Customer, Technician …)
│   └── shared/              # Shared UI, utils, API client, hooks
```

## Local Development Setup

### Prerequisites

- Node.js 20+
- [Expo CLI](https://docs.expo.dev/get-started/installation/): `npm install -g expo-cli`
- iOS Simulator (macOS + Xcode) or Android Emulator (Android Studio)
- Optional: [EAS CLI](https://docs.expo.dev/eas/) for cloud builds

### Getting Started

```bash
# 1. Copy environment variables
cp .env.example .env   # fill in the values described in each comment

# 2. Install dependencies
cd mobile && npm install

# 3. Start the Metro bundler
npx expo start

# Run on a specific platform
npx expo start --ios
npx expo start --android
npx expo start --tunnel     # tunnel mode for physical devices
```

### Checks

```bash
npm run typecheck    # TypeScript strict check
npm test             # Jest (jest-expo preset)
```

## EAS Build

> Requires an Expo account and `eas-cli` installed globally.

```bash
npm install -g eas-cli
eas login
eas build:configure      # validates eas.json — no cloud build triggered
eas build --profile development --platform ios
eas build --profile preview --platform android
eas build --profile production --platform all
```

### Profiles

| Profile       | Distribution | Purpose                          |
|---------------|-------------|----------------------------------|
| `development` | Internal    | Dev client with hot reload; iOS Simulator supported |
| `preview`     | Internal    | QA / stakeholder testing builds  |
| `production`  | Store       | App Store & Google Play releases |

## Environment Variables

| Variable | Description |
|----------|-------------|
| `EXPO_PUBLIC_API_BASE_URL` | Backend base URL (e.g. `https://api.teamhub.online`) |
| `EXPO_PUBLIC_SENTRY_DSN` | Sentry DSN for error monitoring |
| `EXPO_PUBLIC_GOOGLE_MAPS_API_KEY` | Google Maps SDK key (Android & iOS) |

See `.env.example` for inline documentation.
> **TO-DO:** Provision Sentry and Google Maps API key per PRD §26.2 before deploying.

## Known Limitations

- iOS Simulator and Android Emulator integration tests require a macOS dev machine with Xcode / Android Studio installed — these are **not** run in CI.
- EAS cloud builds require an authenticated Expo account; `eas.json` structure is valid and can be verified with `eas build:configure --non-interactive`.
- Push notification setup (APNs / FCM keys) is deferred to the notifications feature task.
