import React, { createContext, useCallback, useContext, useRef } from 'react';
import { useBeforeUnload, useBlocker } from 'react-router-dom';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';

interface UnsavedChangesContextValue {
  setDirty: (isDirty: boolean) => void;
}

const UnsavedChangesContext = createContext<UnsavedChangesContextValue>({
  setDirty: () => undefined,
});

export function useUnsavedChanges(): UnsavedChangesContextValue {
  return useContext(UnsavedChangesContext);
}

export function UnsavedChangesGuard({ children }: { children: React.ReactNode }) {
  const isDirtyRef = useRef(false);

  const setDirty = useCallback((dirty: boolean) => {
    isDirtyRef.current = dirty;
  }, []);

  // Warn on tab close / page unload (browser-native dialog)
  useBeforeUnload(
    useCallback(
      (event) => {
        if (isDirtyRef.current) {
          event.preventDefault();
        }
      },
      [],
    ),
  );

  // Intercept in-app React Router navigation when dirty (PRD §20.5)
  const blocker = useBlocker(
    useCallback(() => isDirtyRef.current, []),
  );

  return (
    <UnsavedChangesContext.Provider value={{ setDirty }}>
      {children}

      {blocker.state === 'blocked' && (
        <Dialog open onOpenChange={(open) => { if (!open) blocker.reset?.(); }}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Unsaved changes</DialogTitle>
              <DialogDescription>
                You have unsaved changes. If you leave this page your changes will be lost.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => blocker.reset?.()}>
                Stay on page
              </Button>
              <Button
                variant="destructive"
                onClick={() => {
                  isDirtyRef.current = false;
                  blocker.proceed?.();
                }}
              >
                Leave anyway
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </UnsavedChangesContext.Provider>
  );
}
