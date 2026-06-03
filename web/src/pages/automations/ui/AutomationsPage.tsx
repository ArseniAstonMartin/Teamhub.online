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
  X,
} from 'lucide-react';
import { Navigate } from 'react-router-dom';

import {
  useAutomations,
  useCreateAutomation,
  useUpdateAutomation,
  useDeleteAutomation,
  useEvaluateAutomations,
} from '@/entities/automation/api/automationsApi';
import type {
  AutomationRuleResponse,
  CreateAutomationRuleRequest,
  MatchRule,
  MatchOperator,
} from '@/entities/automation/model/types';
import { useSchemas } from '@/entities/schema/api/schemasApi';
import type { FieldDefinition } from '@/entities/schema/model/types';
import { useAuthStore } from '@/entities/user/model/authStore';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { Badge } from '@/shared/ui/badge';
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
import { cn } from '@/shared/lib/utils';

// ── Types ─────────────────────────────────────────────────────────────────────

interface MatchConditionDraft {
  op: MatchOperator;
  rawValue: string;
}

interface ActionRowDraft {
  key: string;
  value: string;
}

interface RuleFormState {
  name: string;
  triggerFields: string[];
  matchConditions: Record<string, MatchConditionDraft>;
  actionRows: ActionRowDraft[];
  active: boolean;
}

function emptyForm(): RuleFormState {
  return {
    name: '',
    triggerFields: [],
    matchConditions: {},
    actionRows: [{ key: '', value: '' }],
    active: true,
  };
}

function ruleToForm(rule: AutomationRuleResponse): RuleFormState {
  const matchConditions: Record<string, MatchConditionDraft> = {};
  for (const [fieldKey, mr] of Object.entries(rule.match_rules)) {
    const matchRule = mr as MatchRule;
    let rawValue = '';
    if (matchRule.op === 'eq') {
      rawValue = String(matchRule.value ?? '');
    } else if (matchRule.op === 'in') {
      rawValue = (matchRule.value as unknown[]).join(', ');
    } else if (matchRule.op === 'range') {
      const rv = matchRule.value as { min: number; max: number };
      rawValue = `${rv.min},${rv.max}`;
    }
    matchConditions[fieldKey] = { op: matchRule.op, rawValue };
  }

  const actionRows: ActionRowDraft[] = Object.entries(rule.actions).map(([k, v]) => ({
    key: k,
    value: String(v ?? ''),
  }));

  return {
    name: rule.name,
    triggerFields: [...rule.trigger_fields],
    matchConditions,
    actionRows: actionRows.length > 0 ? actionRows : [{ key: '', value: '' }],
    active: rule.active,
  };
}

function formToRequest(form: RuleFormState): CreateAutomationRuleRequest {
  const match_rules: Record<string, MatchRule> = {};
  for (const fieldKey of form.triggerFields) {
    const cond = form.matchConditions[fieldKey];
    if (!cond) continue;
    if (cond.op === 'eq') {
      match_rules[fieldKey] = { op: 'eq', value: cond.rawValue };
    } else if (cond.op === 'in') {
      match_rules[fieldKey] = {
        op: 'in',
        value: cond.rawValue.split(',').map((s) => s.trim()).filter(Boolean),
      };
    } else if (cond.op === 'range') {
      const parts = cond.rawValue.split(',').map((s) => parseFloat(s.trim()));
      match_rules[fieldKey] = { op: 'range', value: { min: parts[0] ?? 0, max: parts[1] ?? 0 } };
    }
  }

  const actions: Record<string, unknown> = {};
  for (const row of form.actionRows) {
    if (row.key.trim()) {
      actions[row.key.trim()] = row.value;
    }
  }

  return {
    name: form.name,
    trigger_fields: form.triggerFields,
    match_rules,
    actions,
    active: form.active,
  };
}

function actionsSummary(actions: Record<string, unknown>): string {
  const entries = Object.entries(actions);
  if (entries.length === 0) return '—';
  return entries.map(([k, v]) => `Set ${k} = ${String(v)}`).join('; ');
}

// ── Match condition row ───────────────────────────────────────────────────────

interface MatchConditionRowProps {
  fieldDef: FieldDefinition | undefined;
  fieldKey: string;
  condition: MatchConditionDraft;
  onChange: (fieldKey: string, cond: MatchConditionDraft) => void;
}

function MatchConditionRow({ fieldDef, fieldKey, condition, onChange }: MatchConditionRowProps) {
  const label = fieldDef?.label ?? fieldKey;

  return (
    <div className="grid grid-cols-[1fr_100px_1fr] gap-2 items-center">
      <span className="text-sm font-medium truncate" title={label}>
        {label}
      </span>
      <Select
        value={condition.op}
        onValueChange={(v) => onChange(fieldKey, { ...condition, op: v as MatchOperator })}
      >
        <SelectTrigger data-testid={`op-select-${fieldKey}`} className="h-8 text-xs">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="eq">equals</SelectItem>
          <SelectItem value="in">in list</SelectItem>
          <SelectItem value="range">range</SelectItem>
        </SelectContent>
      </Select>
      {condition.op === 'range' ? (
        <div className="flex gap-1">
          <Input
            className="h-8 text-xs"
            placeholder="min"
            value={condition.rawValue.split(',')[0] ?? ''}
            onChange={(e) => {
              const parts = condition.rawValue.split(',');
              parts[0] = e.target.value;
              onChange(fieldKey, { ...condition, rawValue: parts.join(',') });
            }}
          />
          <Input
            className="h-8 text-xs"
            placeholder="max"
            value={condition.rawValue.split(',')[1] ?? ''}
            onChange={(e) => {
              const parts = condition.rawValue.split(',');
              parts[1] = e.target.value;
              onChange(fieldKey, { ...condition, rawValue: parts.join(',') });
            }}
          />
        </div>
      ) : (
        <Input
          className="h-8 text-xs"
          placeholder={condition.op === 'in' ? 'val1, val2, ...' : 'value'}
          value={condition.rawValue}
          onChange={(e) => onChange(fieldKey, { ...condition, rawValue: e.target.value })}
        />
      )}
    </div>
  );
}

// ── Rule Editor Dialog ────────────────────────────────────────────────────────

interface RuleEditorDialogProps {
  open: boolean;
  initial: AutomationRuleResponse | null;
  schemaFields: FieldDefinition[];
  onSave: (req: CreateAutomationRuleRequest) => void;
  onClose: () => void;
  isSaving: boolean;
}

function RuleEditorDialog({
  open,
  initial,
  schemaFields,
  onSave,
  onClose,
  isSaving,
}: RuleEditorDialogProps) {
  const [form, setForm] = useState<RuleFormState>(() =>
    initial ? ruleToForm(initial) : emptyForm(),
  );

  // Reset form when dialog opens/closes or initial changes
  const handleOpenChange = useCallback(
    (o: boolean) => {
      if (!o) onClose();
    },
    [onClose],
  );

  const toggleTriggerField = (key: string) => {
    setForm((prev) => {
      const already = prev.triggerFields.includes(key);
      const triggerFields = already
        ? prev.triggerFields.filter((k) => k !== key)
        : [...prev.triggerFields, key];
      const matchConditions = { ...prev.matchConditions };
      if (!already) {
        matchConditions[key] = matchConditions[key] ?? { op: 'eq', rawValue: '' };
      } else {
        delete matchConditions[key];
      }
      return { ...prev, triggerFields, matchConditions };
    });
  };

  const updateMatchCondition = (fieldKey: string, cond: MatchConditionDraft) => {
    setForm((prev) => ({
      ...prev,
      matchConditions: { ...prev.matchConditions, [fieldKey]: cond },
    }));
  };

  const addActionRow = () => {
    setForm((prev) => ({ ...prev, actionRows: [...prev.actionRows, { key: '', value: '' }] }));
  };

  const removeActionRow = (idx: number) => {
    setForm((prev) => ({
      ...prev,
      actionRows: prev.actionRows.filter((_, i) => i !== idx),
    }));
  };

  const updateActionRow = (idx: number, patch: Partial<ActionRowDraft>) => {
    setForm((prev) => ({
      ...prev,
      actionRows: prev.actionRows.map((row, i) => (i === idx ? { ...row, ...patch } : row)),
    }));
  };

  const isValid =
    form.name.trim().length > 0 &&
    form.triggerFields.length > 0 &&
    form.actionRows.some((r) => r.key.trim().length > 0);

  const handleSave = () => {
    if (!isValid) return;
    onSave(formToRequest(form));
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{initial ? 'Edit Rule' : 'New Automation Rule'}</DialogTitle>
        </DialogHeader>

        <div className="space-y-5 py-2">
          {/* Name */}
          <div className="space-y-1">
            <Label htmlFor="rule-name">Name *</Label>
            <Input
              id="rule-name"
              value={form.name}
              onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
              placeholder="e.g. Auto-assign Toyota jobs"
            />
          </div>

          {/* Trigger fields */}
          <div className="space-y-2">
            <Label>Trigger Fields *</Label>
            <p className="text-xs text-muted-foreground">
              Select the fields that trigger this rule when present in the job.
            </p>
            {schemaFields.length === 0 ? (
              <p className="text-sm text-muted-foreground italic">No schema fields available.</p>
            ) : (
              <div className="flex flex-wrap gap-2" data-testid="trigger-fields-list">
                {schemaFields.map((f) => {
                  const selected = form.triggerFields.includes(f.key);
                  return (
                    <button
                      key={f.key}
                      type="button"
                      onClick={() => toggleTriggerField(f.key)}
                      className={cn(
                        'rounded-full px-3 py-1 text-xs font-medium border transition-colors',
                        selected
                          ? 'bg-primary text-primary-foreground border-primary'
                          : 'bg-muted text-muted-foreground border-border hover:border-primary',
                      )}
                      aria-pressed={selected}
                    >
                      {f.label}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Match conditions */}
          {form.triggerFields.length > 0 && (
            <div className="space-y-2">
              <Label>Match Conditions</Label>
              <div className="space-y-2 rounded-md border p-3">
                {form.triggerFields.map((fk) => {
                  const fieldDef = schemaFields.find((f) => f.key === fk);
                  const cond = form.matchConditions[fk] ?? { op: 'eq' as MatchOperator, rawValue: '' };
                  return (
                    <MatchConditionRow
                      key={fk}
                      fieldDef={fieldDef}
                      fieldKey={fk}
                      condition={cond}
                      onChange={updateMatchCondition}
                    />
                  );
                })}
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="space-y-2">
            <Label>Actions *</Label>
            <p className="text-xs text-muted-foreground">
              Define which fields to auto-fill and what values to set.
            </p>
            <div className="space-y-2">
              {form.actionRows.map((row, idx) => (
                <div key={idx} className="flex gap-2 items-center">
                  <Select
                    value={row.key}
                    onValueChange={(v) => updateActionRow(idx, { key: v })}
                  >
                    <SelectTrigger
                      data-testid={`action-key-select-${idx}`}
                      className="h-8 text-xs flex-1"
                    >
                      <SelectValue placeholder="Field key" />
                    </SelectTrigger>
                    <SelectContent>
                      {schemaFields.map((f) => (
                        <SelectItem key={f.key} value={f.key}>
                          {f.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Input
                    className="h-8 text-xs flex-1"
                    placeholder="value"
                    value={row.value}
                    onChange={(e) => updateActionRow(idx, { value: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => removeActionRow(idx)}
                    className="text-muted-foreground hover:text-destructive"
                    aria-label={`Remove action row ${idx + 1}`}
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
            <Button type="button" variant="outline" size="sm" onClick={addActionRow}>
              <Plus className="h-3 w-3 mr-1" />
              Add action
            </Button>
          </div>

          {/* Active */}
          <div className="flex items-center gap-2">
            <input
              id="rule-active"
              type="checkbox"
              checked={form.active}
              onChange={(e) => setForm((p) => ({ ...p, active: e.target.checked }))}
              className="h-4 w-4"
            />
            <Label htmlFor="rule-active">Active</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isSaving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!isValid || isSaving}>
            {isSaving ? 'Saving…' : initial ? 'Save changes' : 'Create rule'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ── Test Pane ─────────────────────────────────────────────────────────────────

interface TestPaneProps {
  schemaFields: FieldDefinition[];
}

interface DraftFieldRow {
  key: string;
  value: string;
}

function TestPane({ schemaFields: _schemaFields }: TestPaneProps) {
  const [collapsed, setCollapsed] = useState(true);
  const [rows, setRows] = useState<DraftFieldRow[]>([{ key: '', value: '' }]);
  const evaluateMutation = useEvaluateAutomations();

  const addRow = () => setRows((r) => [...r, { key: '', value: '' }]);
  const removeRow = (idx: number) => setRows((r) => r.filter((_, i) => i !== idx));
  const updateRow = (idx: number, patch: Partial<DraftFieldRow>) =>
    setRows((r) => r.map((row, i) => (i === idx ? { ...row, ...patch } : row)));

  const handleEvaluate = async () => {
    const draft_fields: Record<string, unknown> = {};
    for (const row of rows) {
      if (row.key.trim()) draft_fields[row.key.trim()] = row.value;
    }
    try {
      await evaluateMutation.mutateAsync({ draft_fields, manually_set_fields: [] });
    } catch {
      toast.error('Evaluation failed');
    }
  };

  const result = evaluateMutation.data;

  return (
    <div className="rounded-xl border bg-card shadow">
      <button
        type="button"
        onClick={() => setCollapsed((c) => !c)}
        className="flex items-center justify-between w-full px-4 py-3 text-left"
        aria-expanded={!collapsed}
      >
        <span className="font-semibold text-sm">Test Pane</span>
        {collapsed ? (
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        ) : (
          <ChevronUp className="h-4 w-4 text-muted-foreground" />
        )}
      </button>

      {!collapsed && (
        <div className="border-t px-4 py-4 space-y-4">
          <p className="text-xs text-muted-foreground">
            Enter draft job fields and click Evaluate to see which rules match.
          </p>

          <div className="space-y-2">
            {rows.map((row, idx) => (
              <div key={idx} className="flex gap-2 items-center">
                <Input
                  className="h-8 text-xs flex-1"
                  placeholder="field key"
                  value={row.key}
                  onChange={(e) => updateRow(idx, { key: e.target.value })}
                />
                <Input
                  className="h-8 text-xs flex-1"
                  placeholder="value"
                  value={row.value}
                  onChange={(e) => updateRow(idx, { value: e.target.value })}
                />
                <button
                  type="button"
                  onClick={() => removeRow(idx)}
                  className="text-muted-foreground hover:text-destructive"
                  aria-label="Remove test row"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm" onClick={addRow}>
              <Plus className="h-3 w-3 mr-1" />
              Add field
            </Button>
          </div>

          <Button
            onClick={() => void handleEvaluate()}
            disabled={evaluateMutation.isPending}
            data-testid="evaluate-button"
          >
            {evaluateMutation.isPending ? 'Evaluating…' : 'Evaluate'}
          </Button>

          {result && (
            <div className="space-y-3 mt-2">
              {result.auto_filled_fields.length === 0 ? (
                <p className="text-sm text-muted-foreground italic">
                  No rules matched this payload.
                </p>
              ) : (
                <div className="space-y-2">
                  <p className="text-sm font-medium">Auto-filled fields:</p>
                  <table className="w-full text-sm border rounded-md overflow-hidden">
                    <thead className="bg-muted">
                      <tr>
                        <th className="text-left px-3 py-2 font-medium">Field</th>
                        <th className="text-left px-3 py-2 font-medium">Value</th>
                        <th className="text-left px-3 py-2 font-medium">Rule</th>
                      </tr>
                    </thead>
                    <tbody>
                      {result.auto_filled_fields.map((af) => (
                        <tr key={af.field_key} className="border-t">
                          <td className="px-3 py-2 font-mono text-xs">{af.field_key}</td>
                          <td className="px-3 py-2">{String(af.value)}</td>
                          <td className="px-3 py-2">
                            <Badge variant="secondary" data-testid="autofill-badge">
                              Auto-filled by: {af.rule_name}
                            </Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Rule Row ──────────────────────────────────────────────────────────────────

interface RuleRowProps {
  rule: AutomationRuleResponse;
  index: number;
  onToggle: (rule: AutomationRuleResponse) => void;
  onEdit: (rule: AutomationRuleResponse) => void;
  onDelete: (rule: AutomationRuleResponse) => void;
}

function RuleRow({ rule, index, onToggle, onEdit, onDelete }: RuleRowProps) {
  return (
    <Draggable draggableId={rule.id} index={index}>
      {(provided, snapshot) => (
        <div
          ref={provided.innerRef}
          {...provided.draggableProps}
          className={cn(
            'flex items-center gap-3 px-4 py-3 border rounded-lg bg-card transition-shadow',
            snapshot.isDragging && 'shadow-lg ring-2 ring-primary/50',
          )}
        >
          <div {...provided.dragHandleProps} className="cursor-grab text-muted-foreground">
            <GripVertical className="h-4 w-4" />
          </div>
          <span className="text-xs text-muted-foreground w-5 text-right shrink-0">
            {rule.priority}
          </span>
          <span className="flex-1 font-medium text-sm truncate" data-testid="rule-name">
            {rule.name}
          </span>
          <span
            className="text-xs text-muted-foreground truncate max-w-[160px]"
            title={rule.trigger_fields.join(', ')}
            data-testid="rule-triggers"
          >
            {rule.trigger_fields.length > 0 ? rule.trigger_fields.join(', ') : '—'}
          </span>
          <span
            className="text-xs text-muted-foreground truncate max-w-[200px]"
            title={actionsSummary(rule.actions)}
            data-testid="rule-actions-summary"
          >
            {actionsSummary(rule.actions)}
          </span>
          <button
            type="button"
            role="switch"
            aria-checked={rule.active}
            aria-label={`Toggle ${rule.name}`}
            onClick={() => onToggle(rule)}
            className={cn(
              'relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors',
              rule.active ? 'bg-primary' : 'bg-muted',
            )}
            data-testid="active-toggle"
          >
            <span
              className={cn(
                'pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform',
                rule.active ? 'translate-x-4' : 'translate-x-0',
              )}
            />
          </button>
          <button
            type="button"
            onClick={() => onEdit(rule)}
            className="text-muted-foreground hover:text-foreground"
            aria-label={`Edit ${rule.name}`}
          >
            <Pencil className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => onDelete(rule)}
            className="text-muted-foreground hover:text-destructive"
            aria-label={`Delete ${rule.name}`}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      )}
    </Draggable>
  );
}

// ── AutomationsPage ───────────────────────────────────────────────────────────

export default function AutomationsPage() {
  const role = useAuthStore((s) => s.user?.role);

  if (role !== 'ADMIN') {
    return <Navigate to="/schedule" replace />;
  }

  return <AutomationsPageInner />;
}

function AutomationsPageInner() {
  const { data: rules = [], isLoading } = useAutomations();
  const { data: schemas = [] } = useSchemas();
  const createMutation = useCreateAutomation();
  const updateMutation = useUpdateAutomation();
  const deleteMutation = useDeleteAutomation();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<AutomationRuleResponse | null>(null);

  const sortedRules = useMemo(
    () => [...rules].sort((a, b) => a.priority - b.priority),
    [rules],
  );

  const activeSchema = useMemo(() => schemas.find((s) => s.active), [schemas]);
  const schemaFields: FieldDefinition[] = activeSchema?.fieldDefinitions ?? [];

  const openNew = () => {
    setEditingRule(null);
    setDialogOpen(true);
  };

  const openEdit = (rule: AutomationRuleResponse) => {
    setEditingRule(rule);
    setDialogOpen(true);
  };

  const closeDialog = () => {
    setDialogOpen(false);
    setEditingRule(null);
  };

  const handleToggle = async (rule: AutomationRuleResponse) => {
    try {
      await updateMutation.mutateAsync({ id: rule.id, body: { active: !rule.active } });
      toast.success(`Rule "${rule.name}" ${rule.active ? 'disabled' : 'enabled'}`);
    } catch {
      toast.error('Failed to update rule');
    }
  };

  const handleDelete = async (rule: AutomationRuleResponse) => {
    try {
      await deleteMutation.mutateAsync(rule.id);
      toast.success(`Rule "${rule.name}" deleted`);
    } catch {
      toast.error('Failed to delete rule');
    }
  };

  const handleSave = async (req: CreateAutomationRuleRequest) => {
    try {
      if (editingRule) {
        await updateMutation.mutateAsync({ id: editingRule.id, body: req });
        toast.success('Rule updated');
      } else {
        const priority = sortedRules.length > 0
          ? sortedRules[sortedRules.length - 1].priority + 1
          : 1;
        await createMutation.mutateAsync({ ...req, priority });
        toast.success('Rule created');
      }
      closeDialog();
    } catch {
      toast.error('Failed to save rule');
    }
  };

  const handleDragEnd = async (result: DropResult) => {
    if (!result.destination || result.destination.index === result.source.index) return;

    const reordered = [...sortedRules];
    const [moved] = reordered.splice(result.source.index, 1);
    reordered.splice(result.destination.index, 0, moved);

    const patches: Array<Promise<unknown>> = reordered
      .map((rule, idx) => {
        if (rule.priority !== idx + 1) {
          return updateMutation.mutateAsync({ id: rule.id, body: { priority: idx + 1 } });
        }
        return null;
      })
      .filter(Boolean) as Array<Promise<unknown>>;

    try {
      await Promise.all(patches);
      toast.success('Priority updated');
    } catch {
      toast.error('Failed to reorder rules');
    }
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="flex flex-col gap-6 p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Automation Rules</h1>
        <Button onClick={openNew} data-testid="new-rule-button">
          <Plus className="h-4 w-4 mr-2" />
          New Rule
        </Button>
      </div>

      {/* Rule List */}
      <div className="space-y-2">
        {isLoading && (
          <p className="text-sm text-muted-foreground">Loading rules…</p>
        )}
        {!isLoading && sortedRules.length === 0 && (
          <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground text-sm">
            No automation rules yet. Click &quot;New Rule&quot; to create one.
          </div>
        )}
        <DragDropContext onDragEnd={(r) => void handleDragEnd(r)}>
          <Droppable droppableId="rule-list">
            {(provided) => (
              <div ref={provided.innerRef} {...provided.droppableProps} className="space-y-2">
                {sortedRules.map((rule, index) => (
                  <RuleRow
                    key={rule.id}
                    rule={rule}
                    index={index}
                    onToggle={(r) => void handleToggle(r)}
                    onEdit={openEdit}
                    onDelete={(r) => void handleDelete(r)}
                  />
                ))}
                {provided.placeholder}
              </div>
            )}
          </Droppable>
        </DragDropContext>
      </div>

      {/* Test Pane */}
      <TestPane schemaFields={schemaFields} />

      {/* Rule Editor Dialog — key forces remount to reset form state on open/edit change */}
      <RuleEditorDialog
        key={dialogOpen ? (editingRule?.id ?? 'new') : 'closed'}
        open={dialogOpen}
        initial={editingRule}
        schemaFields={schemaFields}
        onSave={(req) => void handleSave(req)}
        onClose={closeDialog}
        isSaving={isSaving}
      />
    </div>
  );
}
