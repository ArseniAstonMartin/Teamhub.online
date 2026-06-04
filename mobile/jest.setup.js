// Suppress the Incompatible React versions warning from react-native-renderer
// in the test environment (React 19.2.6 vs renderer 19.2.3).
// This version drift is a known jest-expo / RN 0.85 issue that does not affect production.
const originalError = console.error.bind(console);
console.error = (...args) => {
  const msg = typeof args[0] === 'string' ? args[0] : '';
  if (msg.includes('Incompatible React versions')) return;
  originalError(...args);
};
