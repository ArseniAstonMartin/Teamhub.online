import { useState, useCallback, useMemo } from 'react';
import {
  DragDropContext,
  Droppable,
  Draggable,
  type DropResult,
} from '@hello-pangea/dnd';
import { toast } from 'sonner';
import {
  GripVertical,
  Plus,
  Pencil,
  Trash2,
  ChevronDown,
  ChevronUp,
  Save,
} from 'lucide-react';

import { useSchemas, usePublishSchema, useDeactivateSchema } from '@/entities/schema/api/schemasApi';
import type { FieldDefinition, FieldType, SchemaResponse } from '@/entities/schema/model/types';
import { FIELD_TYPES } from '@/entities/schema/model/types';
import { useAuthStore } from '@/entities/user/model/authStore';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/shared/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select';
import { Navigate } from 'react-router-dom';

// ── Helpers ──────────────────────────────────────────────────────────────────

function groupSchemasByName(schemas: SchemaResponse[]): Record<string, SchemaResponse[]> {
  return schemas.reduce<Record<string, SchemaResponse[]>>((acc, s) => {
    if (!acc[s.name]) acc[s.name] = [];
    acc[s.name].push(s);
    return acc;
  }, {});
}

function latestByName(schemas: SchemaResponse[]): Record<string, SchemaResponse> {
  const grouped = groupSchemasByName(schemas);
  return Object.fromEntries(
    Object.entries(grouped).map(([name, versions]) => [
      name,
      versions.reduce((a, b) => (a.version > b.version ? a : b)),
    ]),
  );
}

const FIELD_TYPE_LABELS: Record<FieldType, string> = {
  dropdown: 'Dropdown',
  text: 'Text',
  number: 'Number',
  date: 'Date',
  time: 'Time',
  datetime: 'Date & Time',
  file: 'File',
  image: 'Image',
  video: 'Video',
  list: 'List',
  boolean: 'Boolean',
};

// ── Empty field factory ───────────────────────────────────────────────────────

function emptyField(): Omit<FieldDefinition, 'order'> {
  return {
    key: '',
    label: '',
    type: 'text',
    required: false,
    options: null,
    depends_on: null,
    options_by_parent: null,
    validation: null,
  };
}

// ── Field Dialog ─────────────────────────────────────────────────────────────

interface FieldDialogProps {
  open: boolean;
  initial: Partial<FieldDefinition> | null;
  existingFields: FieldDefinition[];
  onSave: (field: Omit<FieldDefinition, 'order'>) => void;
  onClose: () => void;
}

function FieldDialog({ open, initial, existingFields, onSave, onClose }: FieldDialogProps) {
  const base = { ...emptyField(), ...initial };
  const [key, setKey] = useState(base.key ?? '');
  const [label, setLabel] = useState(base.label ?? '');
  const [type, setType] = useState<FieldType>(base.type ?? 'text');
  const [required, setRequired] = useState(base.required ?? false);
  const [optionsRaw, setOptionsRaw] = useState((base.options ?? []).join(', '));
  const [validationMin, setValidationMin] = useState(String(base.validation?.min ?? ''));
  const [validationMax, setValidationMax] = useState(String(base.validation?.max ?? ''));
  const [validationRegex, setValidationRegex] = useState(base.validation?.regex ?? '');
  const [dependsOn, setDependsOn] = useState<string>(base.depends_on ?? '');
  const [optionsByParent, setOptionsByParent] = useState<Record<string, string>>(
    Object.fromEntries(
      Object.entries(base.options_by_parent ?? {}).map(([k, v]) => [k, v.join(', ')]),
    ),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});

  const parentDropdowns = existingFields.filter(
    (f) => f.type === 'dropdown' && f.key !== key,
  );

  const parentField = parentDropdowns.find((f) => f.key === dependsOn);
  const parentOptions = parentField?.options ?? [];

  function validate(): boolean {
    const errs: Record<string, string> = {};
    if (!key.trim()) errs.key = 'Key is required';
    else if (!/^[a-z_]+$/.test(key)) errs.key = 'Only lowercase letters and underscores';
    if (!label.trim()) errs.label = 'Label is required';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  }

  function handleSave() {
    if (!validate()) return;
    const field: Omit<FieldDefinition, 'order'> = {
      key: key.trim(),
      label: label.trim(),
      type,
      required,
      options: type === 'dropdown' ? optionsRaw.split(',').map((s) => s.trim()).filter(Boolean) : null,
      depends_on: type === 'dropdown' && dependsOn ? dependsOn : null,
      options_by_parent:
        type === 'dropdown' && dependsOn
          ? Object.fromEntries(
              Object.entries(optionsByParent).map(([k, v]) => [
                k,
                v.split(',').map((s) => s.trim()).filter(Boolean),
              ]),
            )
          : null,
      validation:
        type === 'text' || type === 'number'
          ? {
              ...(validationMin !== '' ? { min: Number(validationMin) } : {}),
              ...(validationMax !== '' ? { max: Number(validationMax) } : {}),
              ...(validationRegex ? { regex: validationRegex } : {}),
            }
          : null,
    };
    onSave(field);
  }

  // Reset when dialog opens with new initial value
  const handleOpenChange = (o: boolean) => {
    if (!o) onClose();
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{initial?.key ? 'Edit Field' : 'Add Field'}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Key */}
          <div>
            <Label htmlFor="field-key">Key <span className="text-red-500">*</span></Label>
            <Input
              id="field-key"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="vehicle_make"
              className={errors.key ? 'border-red-500' : ''}
              disabled={!!initial?.key}
            />
            {errors.key && <p className="text-xs text-red-500 mt-1">{errors.key}</p>}
          </div>

          {/* Label */}
          <div>
            <Label htmlFor="field-label">Label <span className="text-red-500">*</span></Label>
            <Input
              id="field-label"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder="Vehicle Make"
              className={errors.label ? 'border-red-500' : ''}
            />
            {errors.label && <p className="text-xs text-red-500 mt-1">{errors.label}</p>}
          </div>

          {/* Type */}
          <div>
            <Label htmlFor="field-type">Type</Label>
            <Select value={type} onValueChange={(v) => setType(v as FieldType)}>
              <SelectTrigger id="field-type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {FIELD_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>{FIELD_TYPE_LABELS[t]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Required */}
          <div className="flex items-center gap-2">
            <input
              id="field-required"
              type="checkbox"
              checked={required}
              onChange={(e) => setRequired(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300"
            />
            <Label htmlFor="field-required" className="cursor-pointer">Required</Label>
          </div>

          {/* Options (dropdown only) */}
          {type === 'dropdown' && (
            <div>
              <Label htmlFor="field-options">Options (comma-separated)</Label>
              <textarea
                id="field-options"
                value={optionsRaw}
                onChange={(e) => setOptionsRaw(e.target.value)}
                placeholder="Option A, Option B, Option C"
                className="w-full min-h-[72px] rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm resize-y focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              />
            </div>
          )}

          {/* Depends on (dropdown + other dropdowns exist) */}
          {type === 'dropdown' && parentDropdowns.length > 0 && (
            <div>
              <Label htmlFor="field-depends-on">Depends on</Label>
              <Select
                value={dependsOn || '__none__'}
                onValueChange={(v) => {
                  const val = v === '__none__' ? '' : v;
                  setDependsOn(val);
                  if (!val) setOptionsByParent({});
                }}
              >
                <SelectTrigger id="field-depends-on">
                  <SelectValue placeholder="None" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__none__">None</SelectItem>
                  {parentDropdowns.map((f) => (
                    <SelectItem key={f.key} value={f.key}>{f.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          {/* options_by_parent chip table */}
          {type === 'dropdown' && dependsOn && parentOptions.length > 0 && (
            <div className="space-y-2">
              <Label>Child options per parent value</Label>
              <div className="rounded-md border divide-y">
                {parentOptions.map((pv) => (
                  <div key={pv} className="flex items-start gap-2 px-3 py-2">
                    <span className="text-sm font-medium min-w-[100px] pt-1.5">{pv}</span>
                    <Input
                      value={optionsByParent[pv] ?? ''}
                      onChange={(e) =>
                        setOptionsByParent((prev) => ({ ...prev, [pv]: e.target.value }))
                      }
                      placeholder="Child A, Child B"
                      className="flex-1"
                      aria-label={`Child options for ${pv}`}
                    />
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Validation (text / number only) */}
          {(type === 'text' || type === 'number') && (
            <div className="space-y-3">
              <Label className="text-sm font-medium">Validation (optional)</Label>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label htmlFor="val-min" className="text-xs">Min</Label>
                  <Input
                    id="val-min"
                    type="number"
                    value={validationMin}
                    onChange={(e) => setValidationMin(e.target.value)}
                    placeholder="0"
                  />
                </div>
                <div>
                  <Label htmlFor="val-max" className="text-xs">Max</Label>
                  <Input
                    id="val-max"
                    type="number"
                    value={validationMax}
                    onChange={(e) => setValidationMax(e.target.value)}
                    placeholder="100"
                  />
                </div>
              </div>
              {type === 'text' && (
                <div>
                  <Label htmlFor="val-regex" className="text-xs">Regex pattern</Label>
                  <Input
                    id="val-regex"
                    value={validationRegex}
                    onChange={(e) => setValidationRegex(e.target.value)}
                    placeholder="^[A-Z]{2}.*"
                  />
                </div>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>{initial?.key ? 'Save changes' : 'Add field'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ── Live Preview Pane ─────────────────────────────────────────────────────────

interface PreviewPaneProps {
  fields: FieldDefinition[];
}

function PreviewPane({ fields }: PreviewPaneProps) {
  const sorted = [...fields].sort((a, b) => a.order - b.order);

  return (
    <div className="space-y-4">
      {sorted.length === 0 && (
        <p className="text-sm text-muted-foreground text-center py-8">
          Add fields to preview the form
        </p>
      )}
      {sorted.map((field) => (
        <PreviewField key={field.key} field={field} allFields={fields} />
      ))}
    </div>
  );
}

function PreviewField({ field, allFields }: { field: FieldDefinition; allFields: FieldDefinition[] }) {
  const parentField = field.depends_on
    ? allFields.find((f) => f.key === field.depends_on)
    : null;

  return (
    <div className="space-y-1">
      <label className="text-sm font-medium leading-none">
        {field.label}
        {field.required && <span className="text-red-500 ml-1">*</span>}
      </label>

      {field.type === 'dropdown' ? (
        <select
          disabled
          className="w-full h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm disabled:cursor-not-allowed disabled:opacity-50"
          aria-label={field.label}
        >
          {parentField ? (
            <option>Select {parentField.label} first</option>
          ) : (
            <>
              <option value="">Select an option…</option>
              {(field.options ?? []).map((opt) => (
                <option key={opt} value={opt}>{opt}</option>
              ))}
            </>
          )}
        </select>
      ) : field.type === 'boolean' ? (
        <div className="flex items-center gap-2">
          <input type="checkbox" disabled className="h-4 w-4 rounded border-gray-300" />
          <span className="text-sm text-muted-foreground">{field.label}</span>
        </div>
      ) : field.type === 'file' || field.type === 'image' || field.type === 'video' ? (
        <div className="flex items-center justify-center h-16 rounded-md border-2 border-dashed border-input text-sm text-muted-foreground">
          {FIELD_TYPE_LABELS[field.type]} upload
        </div>
      ) : field.type === 'date' ? (
        <input type="date" disabled className="w-full h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm disabled:opacity-50" />
      ) : field.type === 'time' ? (
        <input type="time" disabled className="w-full h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm disabled:opacity-50" />
      ) : field.type === 'datetime' ? (
        <input type="datetime-local" disabled className="w-full h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm disabled:opacity-50" />
      ) : field.type === 'number' ? (
        <input type="number" disabled placeholder="0" className="w-full h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm disabled:opacity-50" />
      ) : field.type === 'list' ? (
        <div className="flex gap-2">
          <input disabled placeholder="Item…" className="flex-1 h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm disabled:opacity-50" />
          <Button size="sm" disabled variant="outline"><Plus className="h-3 w-3" /></Button>
        </div>
      ) : (
        <input type="text" disabled placeholder={field.label} className="w-full h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm disabled:opacity-50" />
      )}
    </div>
  );
}

// ── Version History ───────────────────────────────────────────────────────────

interface VersionHistoryProps {
  name: string;
  schemas: SchemaResponse[];
}

function VersionHistory({ name, schemas }: VersionHistoryProps) {
  const [open, setOpen] = useState(false);
  const deactivate = useDeactivateSchema();
  const versions = schemas
    .filter((s) => s.name === name)
    .sort((a, b) => b.version - a.version);

  return (
    <div className="rounded-md border">
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between px-4 py-3 text-sm font-medium hover:bg-accent transition-colors"
        aria-expanded={open}
      >
        <span>Version history ({versions.length})</span>
        {open ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
      </button>

      {open && (
        <div className="divide-y">
          {versions.map((v) => (
            <div key={v.id} className="flex items-center justify-between px-4 py-2 text-sm">
              <div className="flex items-center gap-3">
                <span className="font-mono text-xs bg-muted px-1.5 py-0.5 rounded">
                  v{v.version}
                </span>
                <span className="text-muted-foreground">
                  {new Date(v.createdAt).toLocaleDateString()}
                </span>
                {v.active && (
                  <span className="text-xs bg-green-100 text-green-700 px-1.5 py-0.5 rounded-full font-medium">
                    Active
                  </span>
                )}
              </div>
              {v.active && (
                <Button
                  size="sm"
                  variant="outline"
                  className="text-xs h-7"
                  disabled={deactivate.isPending}
                  onClick={() => deactivate.mutate(v.id)}
                >
                  Deactivate
                </Button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function SchemaEditorPage() {
  const user = useAuthStore((s) => s.user);

  if (user && user.role !== 'ADMIN') {
    return <Navigate to="/schedule" replace />;
  }

  return <SchemaEditorContent />;
}

function SchemaEditorContent() {
  const { data: schemas = [], isLoading } = useSchemas();
  const publish = usePublishSchema();

  const schemaNames = useMemo(() => {
    const latest = latestByName(schemas);
    return Object.keys(latest);
  }, [schemas]);

  const latestSchemas = useMemo(() => latestByName(schemas), [schemas]);

  const [selectedName, setSelectedName] = useState<string>('');
  const [fields, setFields] = useState<FieldDefinition[]>([]);
  const [schemaName, setSchemaName] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingField, setEditingField] = useState<FieldDefinition | null>(null);

  const loadSchema = useCallback(
    (name: string) => {
      setSelectedName(name);
      setSchemaName(name);
      const schema = latestSchemas[name];
      if (schema) {
        setFields([...schema.fieldDefinitions].sort((a, b) => a.order - b.order));
      }
    },
    [latestSchemas],
  );

  const handleNewSchema = () => {
    setSelectedName('');
    setSchemaName('');
    setFields([]);
  };

  const handleDragEnd = (result: DropResult) => {
    if (!result.destination) return;
    const reordered = Array.from(fields);
    const [moved] = reordered.splice(result.source.index, 1);
    reordered.splice(result.destination.index, 0, moved);
    setFields(reordered.map((f, i) => ({ ...f, order: i })));
  };

  const openAddDialog = () => {
    setEditingField(null);
    setDialogOpen(true);
  };

  const openEditDialog = (field: FieldDefinition) => {
    setEditingField(field);
    setDialogOpen(true);
  };

  const handleSaveField = (partial: Omit<FieldDefinition, 'order'>) => {
    if (editingField) {
      setFields((prev) =>
        prev.map((f) => (f.key === editingField.key ? { ...partial, order: f.order } : f)),
      );
    } else {
      setFields((prev) => [...prev, { ...partial, order: prev.length }]);
    }
    setDialogOpen(false);
    setEditingField(null);
  };

  const handleDeleteField = (key: string) => {
    setFields((prev) =>
      prev.filter((f) => f.key !== key).map((f, i) => ({ ...f, order: i })),
    );
  };

  const handleSave = async () => {
    if (!schemaName.trim()) {
      toast.error('Schema name is required');
      return;
    }
    const body = {
      name: schemaName.trim(),
      fieldDefinitions: fields.map((f, i) => ({ ...f, order: i })),
    };
    try {
      const result = await publish.mutateAsync(body);
      toast.success(`Schema v${result.version} published`);
      setSelectedName(result.name);
    } catch {
      toast.error('Failed to publish schema');
    }
  };

  return (
    <div className="flex flex-col gap-6 p-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-2xl font-semibold text-brand-navy">Schema Editor</h1>
        <div className="flex items-center gap-3">
          <Select
            value={selectedName || '__new__'}
            onValueChange={(v) => (v === '__new__' ? handleNewSchema() : loadSchema(v))}
          >
            <SelectTrigger className="w-64" aria-label="Select schema">
              <SelectValue placeholder="New schema…" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__new__">— New schema —</SelectItem>
              {schemaNames.map((name) => (
                <SelectItem key={name} value={name}>{name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={handleNewSchema}>
            <Plus className="h-4 w-4 mr-1" />
            New
          </Button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading schemas…</p>
      ) : (
        <>
          {/* Schema name input */}
          <div className="max-w-sm">
            <Label htmlFor="schema-name">Schema name</Label>
            <Input
              id="schema-name"
              value={schemaName}
              onChange={(e) => setSchemaName(e.target.value)}
              placeholder="e.g. Automotive Locksmith Job"
            />
          </div>

          {/* Editor + Preview */}
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
            {/* Editor (left ~60%) */}
            <div className="lg:col-span-3 space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                  Fields
                </h2>
                <Button size="sm" variant="outline" onClick={openAddDialog}>
                  <Plus className="h-3 w-3 mr-1" />
                  Add Field
                </Button>
              </div>

              <DragDropContext onDragEnd={handleDragEnd}>
                <Droppable droppableId="fields">
                  {(provided) => (
                    <div
                      ref={provided.innerRef}
                      {...provided.droppableProps}
                      className="space-y-2 min-h-[40px]"
                    >
                      {fields.map((field, index) => (
                        <Draggable key={field.key} draggableId={field.key} index={index}>
                          {(drag, snapshot) => (
                            <div
                              ref={drag.innerRef}
                              {...drag.draggableProps}
                              className={`flex items-center gap-2 rounded-md border bg-card px-3 py-2 text-sm transition-shadow ${
                                snapshot.isDragging ? 'shadow-lg' : ''
                              }`}
                            >
                              <span
                                {...drag.dragHandleProps}
                                className="text-muted-foreground hover:text-foreground cursor-grab"
                                aria-label="Drag to reorder"
                              >
                                <GripVertical className="h-4 w-4" />
                              </span>

                              <span className="flex-1 font-medium">{field.label}</span>

                              <span className="text-xs bg-muted text-muted-foreground px-1.5 py-0.5 rounded">
                                {FIELD_TYPE_LABELS[field.type]}
                              </span>

                              {field.required && (
                                <span className="text-xs text-red-500 font-semibold">*</span>
                              )}

                              <button
                                onClick={() => openEditDialog(field)}
                                className="text-muted-foreground hover:text-foreground transition-colors"
                                aria-label={`Edit ${field.label}`}
                              >
                                <Pencil className="h-3.5 w-3.5" />
                              </button>
                              <button
                                onClick={() => handleDeleteField(field.key)}
                                className="text-muted-foreground hover:text-destructive transition-colors"
                                aria-label={`Delete ${field.label}`}
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            </div>
                          )}
                        </Draggable>
                      ))}
                      {provided.placeholder}
                    </div>
                  )}
                </Droppable>
              </DragDropContext>

              {fields.length === 0 && (
                <div className="flex items-center justify-center h-24 rounded-md border-2 border-dashed text-sm text-muted-foreground">
                  No fields yet — click &quot;Add Field&quot; to start
                </div>
              )}

              <div className="flex justify-end pt-2">
                <Button
                  onClick={handleSave}
                  disabled={publish.isPending}
                  className="gap-2"
                >
                  <Save className="h-4 w-4" />
                  {publish.isPending ? 'Publishing…' : 'Save as new version'}
                </Button>
              </div>
            </div>

            {/* Preview (right ~40%) */}
            <div className="lg:col-span-2">
              <div className="rounded-md border bg-card p-4 space-y-4">
                <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                  Live Preview
                </h2>
                <PreviewPane fields={fields} />
              </div>
            </div>
          </div>

          {/* Version history */}
          {selectedName && (
            <VersionHistory name={selectedName} schemas={schemas} />
          )}
        </>
      )}

      {/* Field dialog — key forces remount so useState re-initializes when editing a different field */}
      <FieldDialog
        key={editingField?.key ?? '__new__'}
        open={dialogOpen}
        initial={editingField}
        existingFields={fields}
        onSave={handleSaveField}
        onClose={() => {
          setDialogOpen(false);
          setEditingField(null);
        }}
      />
    </div>
  );
}
