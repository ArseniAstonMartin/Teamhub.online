import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { X } from 'lucide-react';

import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select';
import { AddressAutocomplete } from '@/shared/ui/AddressAutocomplete';
import { useSchemas } from '@/entities/schema/api/schemasApi';
import { useCreateJob } from '@/entities/job/api/jobsApi';
import { useEvaluateAutomations } from '@/entities/automation/api/automationsApi';
import type { FieldDefinition } from '@/entities/schema/model/types';
import type { AutoFilledField } from '@/entities/automation/model/types';

// ── Types ────────────────────────────────────────────────────────────────────

type FormValues = Record<string, unknown>;

interface AutoFillState {
  [fieldKey: string]: AutoFilledField;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

const ADDRESS_KEYS = new Set(['address', 'address_line']);

function isAddressField(key: string): boolean {
  return ADDRESS_KEYS.has(key);
}

function getStringValue(v: unknown): string {
  if (v === null || v === undefined) return '';
  return String(v);
}

function omitKey<T extends Record<string, unknown>>(obj: T, key: string): Omit<T, string> {
  return Object.fromEntries(Object.entries(obj).filter(([k]) => k !== key)) as Omit<T, string>;
}

// ── SchemaField renderer ─────────────────────────────────────────────────────

interface FieldProps {
  field: FieldDefinition;
  value: unknown;
  parentValue: unknown;
  autoFill: AutoFilledField | undefined;
  errors: Record<string, string>;
  onChange: (key: string, value: unknown, explicit?: boolean) => void;
  onUndoAutoFill: (key: string) => void;
}

function SchemaField({
  field,
  value,
  parentValue,
  autoFill,
  errors,
  onChange,
  onUndoAutoFill,
}: FieldProps) {
  const { key, label, type, required, depends_on, options, options_by_parent, validation } = field;

  const isDisabled = Boolean(depends_on && !parentValue);

  const availableOptions = useMemo<string[]>(() => {
    if (depends_on && options_by_parent) {
      const parentStr = getStringValue(parentValue);
      return options_by_parent[parentStr] ?? [];
    }
    return options ?? [];
  }, [depends_on, options_by_parent, options, parentValue]);

  const fieldId = `field-${key}`;
  const errorMsg = errors[key];

  const handleChange = (newVal: unknown) => onChange(key, newVal, true);

  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2 flex-wrap">
        <Label htmlFor={fieldId} className="text-sm font-medium text-brand-navy">
          {label}
          {required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        {autoFill && (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 border border-amber-300 px-2 py-0.5 text-xs text-amber-800">
            Auto-filled by {autoFill.rule_name}
            <button
              type="button"
              aria-label={`Undo auto-fill for ${label}`}
              className="ml-0.5 rounded-full hover:bg-amber-200 p-0.5"
              onClick={() => onUndoAutoFill(key)}
            >
              <X className="size-3" />
            </button>
          </span>
        )}
      </div>

      {/* Address field */}
      {isAddressField(key) && (
        <AddressAutocomplete
          id={fieldId}
          value={getStringValue(value)}
          disabled={isDisabled}
          aria-label={label}
          onChange={(addrVal) => {
            onChange(key, addrVal.address, true);
            if (addrVal.lat !== null) onChange('lat', addrVal.lat, false);
            if (addrVal.lon !== null) onChange('lon', addrVal.lon, false);
            if (addrVal.city) onChange('city', addrVal.city, false);
            if (addrVal.state) onChange('state', addrVal.state, false);
            if (addrVal.zip) onChange('zip', addrVal.zip, false);
          }}
        />
      )}

      {/* Text */}
      {!isAddressField(key) && type === 'text' && (
        <Input
          id={fieldId}
          type="text"
          value={getStringValue(value)}
          disabled={isDisabled}
          aria-required={required}
          aria-invalid={Boolean(errorMsg)}
          minLength={validation?.min}
          maxLength={validation?.max}
          pattern={validation?.regex ?? undefined}
          onChange={(e) => handleChange(e.target.value)}
        />
      )}

      {/* Number */}
      {type === 'number' && (
        <Input
          id={fieldId}
          type="number"
          value={getStringValue(value)}
          disabled={isDisabled}
          aria-required={required}
          aria-invalid={Boolean(errorMsg)}
          min={validation?.min}
          max={validation?.max}
          onChange={(e) => handleChange(e.target.value === '' ? '' : Number(e.target.value))}
        />
      )}

      {/* Date / time / datetime */}
      {(type === 'date' || type === 'time' || type === 'datetime') && (
        <Input
          id={fieldId}
          type={type === 'datetime' ? 'datetime-local' : type}
          value={getStringValue(value)}
          disabled={isDisabled}
          aria-required={required}
          aria-invalid={Boolean(errorMsg)}
          onChange={(e) => handleChange(e.target.value)}
        />
      )}

      {/* Boolean */}
      {type === 'boolean' && (
        <div className="flex items-center gap-2">
          <input
            id={fieldId}
            type="checkbox"
            className="h-4 w-4 rounded border-input accent-primary"
            checked={Boolean(value)}
            disabled={isDisabled}
            aria-required={required}
            onChange={(e) => handleChange(e.target.checked)}
          />
        </div>
      )}

      {/* Dropdown */}
      {type === 'dropdown' && (
        <Select
          value={getStringValue(value)}
          disabled={isDisabled}
          onValueChange={(v) => handleChange(v)}
        >
          <SelectTrigger
            id={fieldId}
            aria-required={required}
            aria-invalid={Boolean(errorMsg)}
            aria-label={label}
          >
            <SelectValue placeholder={`Select ${label}`} />
          </SelectTrigger>
          <SelectContent>
            {availableOptions.map((opt) => (
              <SelectItem key={opt} value={opt}>
                {opt}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}

      {/* List — multi-select checkboxes */}
      {type === 'list' && (
        <div
          className="space-y-1"
          role="group"
          aria-labelledby={fieldId}
          aria-required={required}
        >
          {(options ?? []).map((opt) => {
            const checked = Array.isArray(value) ? (value as string[]).includes(opt) : false;
            return (
              <label key={opt} className="flex items-center gap-2 text-sm cursor-pointer">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-input accent-primary"
                  checked={checked}
                  disabled={isDisabled}
                  onChange={(e) => {
                    const current = Array.isArray(value) ? (value as string[]) : [];
                    const next = e.target.checked
                      ? [...current, opt]
                      : current.filter((v) => v !== opt);
                    handleChange(next);
                  }}
                />
                {opt}
              </label>
            );
          })}
        </div>
      )}

      {/* File / image / video — placeholder */}
      {(type === 'file' || type === 'image' || type === 'video') && (
        /* TO-DO: file upload not yet implemented — requires storage integration */
        <input
          id={fieldId}
          type="file"
          disabled
          aria-label={label}
          className="block w-full text-sm text-brand-muted cursor-not-allowed opacity-50"
          accept={type === 'image' ? 'image/*' : type === 'video' ? 'video/*' : undefined}
        />
      )}

      {errorMsg && (
        <p className="text-xs text-destructive" role="alert" id={`${fieldId}-error`}>
          {errorMsg}
        </p>
      )}
    </div>
  );
}

// ── Form body (separate component so `key` prop resets state on open) ─────────

interface FormProps {
  schemaId: string;
  sortedFields: FieldDefinition[];
  onClose: () => void;
}

function JobCreateForm({ schemaId, sortedFields, onClose }: FormProps) {
  const createJob = useCreateJob();
  const evaluateAutomations = useEvaluateAutomations();

  const [formValues, setFormValues] = useState<FormValues>({});
  const [manuallySetFields, setManuallySetFields] = useState<string[]>([]);
  const [autoFills, setAutoFills] = useState<AutoFillState>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  // ── Field change handler ────────────────────────────────────────────────────

  const handleFieldChange = useCallback(
    (key: string, value: unknown, explicit = true) => {
      setFormValues((prev) => {
        const next = { ...prev, [key]: value };
        // Clear dependent fields whose parent just changed
        sortedFields
          .filter((f) => f.depends_on === key)
          .forEach((f) => {
            next[f.key] = '';
          });
        return next;
      });

      if (explicit) {
        setManuallySetFields((prev) => (prev.includes(key) ? prev : [...prev, key]));
        setAutoFills((prev) => (prev[key] ? omitKey(prev, key) : prev));
      }

      setErrors((prev) => (prev[key] ? omitKey(prev, key) : prev));
    },
    [sortedFields],
  );

  // ── Auto-fill undo ──────────────────────────────────────────────────────────

  const handleUndoAutoFill = useCallback((key: string) => {
    setAutoFills((prev) => omitKey(prev, key));
    setFormValues((prev) => ({ ...prev, [key]: '' }));
    setManuallySetFields((prev) => (prev.includes(key) ? prev : [...prev, key]));
  }, []);

  // ── Automation evaluate (debounced 300ms) ───────────────────────────────────

  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (debounceTimer.current) clearTimeout(debounceTimer.current);

    const snapshot = manuallySetFields;

    debounceTimer.current = setTimeout(() => {
      void evaluateAutomations
        .mutateAsync({ draft_fields: formValues, manually_set_fields: snapshot })
        .then((result) => {
          result.auto_filled_fields.forEach((af) => {
            if (!snapshot.includes(af.field_key)) {
              setAutoFills((prev) => ({ ...prev, [af.field_key]: af }));
              setFormValues((fv) => ({ ...fv, [af.field_key]: af.value }));
            }
          });
        })
        .catch(() => {
          // Automation errors are non-fatal; continue without auto-fill
        });
    }, 300);

    return () => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues]);

  // ── Validation ──────────────────────────────────────────────────────────────

  function validate(): boolean {
    const newErrors: Record<string, string> = {};

    sortedFields.forEach((field) => {
      const val = formValues[field.key];
      if (field.required) {
        const empty =
          val === undefined ||
          val === null ||
          val === '' ||
          (Array.isArray(val) && val.length === 0);
        if (empty) newErrors[field.key] = `${field.label} is required`;
      }

      if (field.validation?.regex && typeof val === 'string' && val !== '') {
        if (!new RegExp(field.validation.regex).test(val)) {
          newErrors[field.key] = `${field.label} format is invalid`;
        }
      }

      if (field.validation?.min !== undefined && typeof val === 'number' && val < field.validation.min) {
        newErrors[field.key] = `${field.label} must be at least ${field.validation.min}`;
      }

      if (field.validation?.max !== undefined && typeof val === 'number' && val > field.validation.max) {
        newErrors[field.key] = `${field.label} must be at most ${field.validation.max}`;
      }
    });

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }

  // ── Submit ──────────────────────────────────────────────────────────────────

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;

    setSubmitError(null);

    const reservedKeys = new Set(['lat', 'lon', 'scheduledStart', 'scheduledEnd', 'customerId']);
    const customFields: Record<string, unknown> = {};
    Object.entries(formValues).forEach(([k, v]) => {
      if (!reservedKeys.has(k)) customFields[k] = v;
    });

    try {
      await createJob.mutateAsync({
        customerId: formValues['customerId'] as string | undefined,
        appointmentSchemaId: schemaId,
        customFields,
        scheduledStart: formValues['scheduledStart'] as string | undefined,
        scheduledEnd: formValues['scheduledEnd'] as string | undefined,
        lat: formValues['lat'] as number | undefined,
        lon: formValues['lon'] as number | undefined,
      });
      onClose();
    } catch {
      setSubmitError('Failed to create job. Please try again.');
    }
  }

  const isSubmitting = createJob.isPending;

  return (
    <>
      <form
        id="job-create-form"
        onSubmit={(e) => {
          void handleSubmit(e);
        }}
        noValidate
      >
        <div className="space-y-4 py-2">
          {sortedFields.map((field) => {
            const parentValue = field.depends_on ? formValues[field.depends_on] : undefined;
            return (
              <SchemaField
                key={field.key}
                field={field}
                value={formValues[field.key] ?? ''}
                parentValue={parentValue}
                autoFill={autoFills[field.key]}
                errors={errors}
                onChange={handleFieldChange}
                onUndoAutoFill={handleUndoAutoFill}
              />
            );
          })}
        </div>

        {submitError && (
          <p className="text-sm text-destructive mt-2" role="alert">
            {submitError}
          </p>
        )}
      </form>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button
          type="submit"
          form="job-create-form"
          disabled={isSubmitting}
          aria-disabled={isSubmitting}
        >
          {isSubmitting ? 'Creating…' : 'Create Job'}
        </Button>
      </DialogFooter>
    </>
  );
}

// ── Main dialog ───────────────────────────────────────────────────────────────

interface Props {
  open: boolean;
  onClose: () => void;
}

export function JobCreateDialog({ open, onClose }: Props) {
  const { data: schemas } = useSchemas();
  const activeSchema = useMemo(() => schemas?.find((s) => s.active) ?? null, [schemas]);
  const sortedFields = useMemo<FieldDefinition[]>(
    () =>
      activeSchema
        ? [...activeSchema.fieldDefinitions].sort((a, b) => a.order - b.order)
        : [],
    [activeSchema],
  );

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>New Job</DialogTitle>
        </DialogHeader>

        {!activeSchema && (
          <>
            <p className="text-sm text-brand-muted py-4 text-center">
              No active schema found. Please configure an appointment schema first.
            </p>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>
                Cancel
              </Button>
            </DialogFooter>
          </>
        )}

        {activeSchema && (
          <JobCreateForm
            key={open ? 'open' : 'closed'}
            schemaId={activeSchema.id}
            sortedFields={sortedFields}
            onClose={onClose}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
