/**
 * Feature flags for features planned in v1.2+.
 * Flip v1_2_preview to true to show placeholder tabs during internal testing.
 */
export const featureFlags = {
  // TO-DO: load from a remote config source (e.g. Expo Updates config) in v1.2
  v1_2_preview: false,
} as const;
