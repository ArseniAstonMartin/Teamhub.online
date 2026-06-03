export type FieldType =
  | 'dropdown'
  | 'text'
  | 'number'
  | 'date'
  | 'time'
  | 'datetime'
  | 'file'
  | 'image'
  | 'video'
  | 'list'
  | 'boolean';

export const FIELD_TYPES: FieldType[] = [
  'dropdown',
  'text',
  'number',
  'date',
  'time',
  'datetime',
  'file',
  'image',
  'video',
  'list',
  'boolean',
];

export interface FieldValidation {
  min?: number;
  max?: number;
  regex?: string;
}

export interface FieldDefinition {
  key: string;
  label: string;
  type: FieldType;
  required: boolean;
  order: number;
  options: string[] | null;
  depends_on: string | null;
  options_by_parent: Record<string, string[]> | null;
  validation: FieldValidation | null;
}

export interface SchemaResponse {
  id: string;
  name: string;
  version: number;
  active: boolean;
  fieldDefinitions: FieldDefinition[];
  createdAt: string;
}

export interface PublishSchemaRequest {
  name: string;
  fieldDefinitions: FieldDefinition[];
}
