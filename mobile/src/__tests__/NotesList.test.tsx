import React from 'react';
import { render } from '@testing-library/react-native';
import NotesList from '../components/NotesList';
import type { NoteItem } from '../components/NotesList';

describe('NotesList — offline pending badge', () => {
  it('shows Pending sync badge for offline notes', () => {
    const notes: NoteItem[] = [
      {
        id: 'local-1',
        body: 'Pending offline note',
        authorUserId: '',
        createdAt: new Date().toISOString(),
        pending: true,
      },
      {
        id: 'server-1',
        body: 'Synced note',
        authorUserId: 'user-1',
        createdAt: new Date().toISOString(),
        pending: false,
      },
    ];

    const { getByTestId, queryByTestId } = render(<NotesList notes={notes} />);
    expect(getByTestId('pending-badge-local-1')).toBeTruthy();
    expect(queryByTestId('pending-badge-server-1')).toBeNull();
  });
});
