import { Animated } from 'react-native';

export function createShakeAnimation(animatedValue: Animated.Value) {
  return Animated.sequence([
    Animated.timing(animatedValue, { toValue: 10, duration: 50, useNativeDriver: false }),
    Animated.timing(animatedValue, { toValue: -10, duration: 50, useNativeDriver: false }),
    Animated.timing(animatedValue, { toValue: 6, duration: 50, useNativeDriver: false }),
    Animated.timing(animatedValue, { toValue: -6, duration: 50, useNativeDriver: false }),
    Animated.timing(animatedValue, { toValue: 0, duration: 50, useNativeDriver: false }),
  ]);
}
