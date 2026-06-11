import { useEffect, useId, useRef, useState } from 'react';
import { useCustomers } from '@/entities/customer/api/customersApi';
import type { CustomerResponse } from '@/entities/customer/model/types';
import { AddressAutocomplete } from '@/shared/ui/AddressAutocomplete';
import type { AddressValue } from '@/shared/ui/AddressAutocomplete';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { cn } from '@/shared/lib/utils';

export interface NewCustomerDraft {
  name: string;
  companyName: string;
  phone: string;
  email: string;
  addressLine: string;
  city: string;
  state: string;
  zip: string;
  lat: number | null;
  lon: number | null;
}

export const E164_PHONE_REGEX = /^\+[1-9]\d{7,14}$/;

/** PRD §8.1 suggestion line: Name - Email - Company - Phone */
export function formatCustomerSuggestion(customer: CustomerResponse): string {
  const parts: string[] = [customer.name];
  if (customer.email) parts.push(customer.email);
  if (customer.companyName) parts.push(customer.companyName);
  parts.push(customer.phone);
  return parts.join(' - ');
}

export const EMPTY_CUSTOMER_DRAFT: NewCustomerDraft = {
  name: '',
  companyName: '',
  phone: '',
  email: '',
  addressLine: '',
  city: '',
  state: '',
  zip: '',
  lat: null,
  lon: null,
};

interface CustomerPickerProps {
  selectedCustomer: CustomerResponse | null;
  onSelectCustomer: (customer: CustomerResponse) => void;
  onClearSelection: () => void;
  draft: NewCustomerDraft;
  onDraftChange: (draft: NewCustomerDraft) => void;
  errors?: {
    customer?: string;
    name?: string;
    phone?: string;
    email?: string;
    address?: string;
  };
}

export function CustomerPicker({
  selectedCustomer,
  onSelectCustomer,
  onClearSelection,
  draft,
  onDraftChange,
  errors = {},
}: CustomerPickerProps) {
  const listboxId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(searchQuery.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const { data: suggestionsPage } = useCustomers({
    q: debouncedQuery || undefined,
    size: 8,
  });
  const suggestions = suggestionsPage?.content ?? [];

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setSuggestionsOpen(false);
        setActiveIndex(-1);
      }
    }
    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, []);

  function updateDraft(patch: Partial<NewCustomerDraft>) {
    onDraftChange({ ...draft, ...patch });
  }

  function handleNameChange(value: string) {
    setSearchQuery(value);
    updateDraft({ name: value });
    setSuggestionsOpen(value.trim().length > 0);
    setActiveIndex(-1);
  }

  function handleSelectSuggestion(customer: CustomerResponse) {
    onSelectCustomer(customer);
    setSearchQuery('');
    setSuggestionsOpen(false);
    setActiveIndex(-1);
  }

  function handleClearSelection() {
    onClearSelection();
    setSearchQuery(draft.name);
    setSuggestionsOpen(false);
    setActiveIndex(-1);
  }

  function handleNameKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!suggestionsOpen || suggestions.length === 0) return;

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex((prev) => (prev + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((prev) => (prev <= 0 ? suggestions.length - 1 : prev - 1));
    } else if (event.key === 'Enter' && activeIndex >= 0) {
      event.preventDefault();
      handleSelectSuggestion(suggestions[activeIndex]!);
    } else if (event.key === 'Escape') {
      setSuggestionsOpen(false);
      setActiveIndex(-1);
    }
  }

  if (selectedCustomer) {
    return (
      <div className="space-y-1" data-testid="customer-picker-selected">
        <Label className="text-sm font-medium text-brand-navy">
          Customer
          <span className="text-destructive ml-0.5">*</span>
        </Label>
        <div className="flex items-start gap-2 rounded-md border border-green-200 bg-green-50 px-3 py-2">
          <p className="flex-1 text-sm text-brand-navy">{formatCustomerSuggestion(selectedCustomer)}</p>
          <button
            type="button"
            className="shrink-0 text-xs font-medium text-primary hover:underline"
            onClick={handleClearSelection}
          >
            Change
          </button>
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className="space-y-3" data-testid="customer-picker">
      <div className="space-y-1 relative">
        <Label htmlFor="customer-name" className="text-sm font-medium text-brand-navy">
          Name
          <span className="text-destructive ml-0.5">*</span>
        </Label>
        <Input
          id="customer-name"
          type="text"
          value={draft.name}
          autoComplete="off"
          aria-required
          aria-invalid={Boolean(errors.name || errors.customer)}
          aria-expanded={suggestionsOpen && suggestions.length > 0}
          aria-controls={suggestionsOpen ? listboxId : undefined}
          aria-autocomplete="list"
          placeholder="Search or enter customer name"
          onChange={(e) => handleNameChange(e.target.value)}
          onFocus={() => {
            if (draft.name.trim()) setSuggestionsOpen(true);
          }}
          onKeyDown={handleNameKeyDown}
        />
        {suggestionsOpen && suggestions.length > 0 && (
          <ul
            id={listboxId}
            role="listbox"
            className="absolute z-20 mt-1 max-h-48 w-full overflow-auto rounded-md border bg-white py-1 shadow-md"
            data-testid="customer-suggestions"
          >
            {suggestions.map((customer, index) => (
              <li key={customer.id} role="presentation">
                <button
                  type="button"
                  role="option"
                  aria-selected={index === activeIndex}
                  className={cn(
                    'w-full px-3 py-2 text-left text-sm hover:bg-gray-50',
                    index === activeIndex && 'bg-gray-100',
                  )}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => handleSelectSuggestion(customer)}
                >
                  {formatCustomerSuggestion(customer)}
                </button>
              </li>
            ))}
          </ul>
        )}
        {(errors.name || errors.customer) && (
          <p className="text-xs text-destructive" role="alert">
            {errors.name ?? errors.customer}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="customer-company" className="text-sm font-medium text-brand-navy">
          Company name
        </Label>
        <Input
          id="customer-company"
          type="text"
          value={draft.companyName}
          onChange={(e) => updateDraft({ companyName: e.target.value })}
        />
      </div>

      <div className="space-y-1">
        <Label htmlFor="customer-phone" className="text-sm font-medium text-brand-navy">
          Phone number
          <span className="text-destructive ml-0.5">*</span>
        </Label>
        <Input
          id="customer-phone"
          type="tel"
          value={draft.phone}
          aria-required
          aria-invalid={Boolean(errors.phone)}
          placeholder="+15033334444"
          onChange={(e) => updateDraft({ phone: e.target.value })}
        />
        {errors.phone && (
          <p className="text-xs text-destructive" role="alert">
            {errors.phone}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="customer-email" className="text-sm font-medium text-brand-navy">
          Email
        </Label>
        <Input
          id="customer-email"
          type="email"
          value={draft.email}
          aria-invalid={Boolean(errors.email)}
          onChange={(e) => updateDraft({ email: e.target.value })}
        />
        {errors.email && (
          <p className="text-xs text-destructive" role="alert">
            {errors.email}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="customer-address" className="text-sm font-medium text-brand-navy">
          Address
          <span className="text-destructive ml-0.5">*</span>
        </Label>
        <AddressAutocomplete
          id="customer-address"
          value={draft.addressLine}
          aria-label="Address"
          placeholder="Service address"
          onChange={(addr: AddressValue) =>
            updateDraft({
              addressLine: addr.address,
              city: addr.city,
              state: addr.state,
              zip: addr.zip,
              lat: addr.lat,
              lon: addr.lon,
            })
          }
        />
        {errors.address && (
          <p className="text-xs text-destructive" role="alert">
            {errors.address}
          </p>
        )}
      </div>

      <p className="text-xs text-brand-muted">
        Pick a suggestion to use an existing customer, or fill in the fields to create a new one when
        you submit the job.
      </p>
    </div>
  );
}
