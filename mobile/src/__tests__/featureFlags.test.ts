import { featureFlags } from '../config/featureFlags';

describe('featureFlags', () => {
  it('v1_2_preview defaults to false', () => {
    expect(featureFlags.v1_2_preview).toBe(false);
  });
});
