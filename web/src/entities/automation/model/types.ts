export interface AutomationRuleResponse {
  id: string;
  tenant_id: string;
  name: string;
  trigger_fields: string[];
  match_rules: Record<string, MatchRule>;
  actions: Record<string, unknown>;
  active: boolean;
  priority: number;
  archived_at: string | null;
  created_at: string;
  updated_at: string;
}

export type MatchOperator = 'eq' | 'in' | 'range';

export interface MatchRuleEq {
  op: 'eq';
  value: unknown;
}

export interface MatchRuleIn {
  op: 'in';
  value: unknown[];
}

export interface MatchRuleRange {
  op: 'range';
  value: { min: number; max: number };
}

export type MatchRule = MatchRuleEq | MatchRuleIn | MatchRuleRange;

export interface CreateAutomationRuleRequest {
  name: string;
  trigger_fields: string[];
  match_rules: Record<string, MatchRule>;
  actions: Record<string, unknown>;
  active?: boolean;
  priority?: number;
}

export interface UpdateAutomationRuleRequest {
  name?: string;
  trigger_fields?: string[];
  match_rules?: Record<string, MatchRule>;
  actions?: Record<string, unknown>;
  active?: boolean;
  priority?: number;
}

export interface EvaluateAutomationsRequest {
  draft_fields: Record<string, unknown>;
  manually_set_fields?: string[];
}

export interface AutoFilledField {
  field_key: string;
  value: unknown;
  rule_id: string;
  rule_name: string;
}

export interface EvaluateAutomationsResponse {
  suggested_patch: Record<string, unknown>;
  auto_filled_fields: AutoFilledField[];
}
