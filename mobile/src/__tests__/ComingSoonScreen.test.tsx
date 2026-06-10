import React from 'react';
import { render } from '@testing-library/react-native';
import ComingSoonScreen from '../screens/ComingSoonScreen';

describe('ComingSoonScreen', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('renders the title prop', () => {
    const { getByText } = render(
      <ComingSoonScreen title="Test Title" subtitle="Test Subtitle" />,
    );
    expect(getByText('Test Title')).toBeTruthy();
  });

  it('renders the subtitle prop', () => {
    const { getByText } = render(
      <ComingSoonScreen title="Test Title" subtitle="Test Subtitle" />,
    );
    expect(getByText('Test Subtitle')).toBeTruthy();
  });
});
