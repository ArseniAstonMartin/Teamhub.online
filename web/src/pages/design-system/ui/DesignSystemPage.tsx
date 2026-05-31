import { useState } from 'react';
import { toast } from 'sonner';
import { Bell, CheckCircle, AlertTriangle, AlertCircle, Info } from 'lucide-react';

import { Button } from '@/shared/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog';
import { Badge } from '@/shared/ui/badge';
import { Toaster } from '@/shared/ui/sonner';

const Section = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <section className="space-y-4">
    <h2 className="text-xl font-semibold text-foreground border-b border-border pb-2">{title}</h2>
    {children}
  </section>
);

const ColorSwatch = ({
  label,
  className,
  textClass = 'text-white',
}: {
  label: string;
  className: string;
  textClass?: string;
}) => (
  <div className={`${className} rounded-lg p-4 flex items-center justify-center`}>
    <span className={`text-xs font-semibold ${textClass}`}>{label}</span>
  </div>
);

export default function DesignSystemPage() {
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <div className="min-h-screen bg-background text-foreground">
      <Toaster />

      {/* Header */}
      <header className="border-b border-border bg-card px-8 py-6">
        <h1 className="text-3xl font-bold text-foreground">Teamhub Design System</h1>
        <p className="mt-1 text-muted-foreground">
          Brand tokens, primitives, and component showcase — PRD §20
        </p>
      </header>

      <main className="mx-auto max-w-5xl space-y-12 px-8 py-12">

        {/* Typography */}
        <Section title="Typography — Manrope">
          <div className="space-y-3">
            <h1 className="text-4xl font-bold text-foreground">Page Title — h1 bold</h1>
            <h2 className="text-2xl font-semibold text-foreground">Section Title — h2 semibold</h2>
            <h3 className="text-lg font-semibold text-foreground">Card Title — h3 semibold</h3>
            <h4 className="text-base font-medium text-foreground">Label — h4 medium</h4>
            <p className="text-sm text-foreground">
              Body text — regular weight, readable data in tables, descriptions, and detail panes.
            </p>
            <p className="text-sm text-muted-foreground">
              Muted text — secondary labels, helper text, captions.
            </p>
          </div>
        </Section>

        {/* Color Palette */}
        <Section title="Color Palette — Brand Tokens">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
            <ColorSwatch label="brand.blue" className="bg-brand-blue" />
            <ColorSwatch label="brand.indigo" className="bg-brand-indigo" />
            <ColorSwatch label="brand.cyan" className="bg-brand-cyan" />
            <ColorSwatch label="brand.success" className="bg-brand-success" />
            <ColorSwatch label="brand.warning" className="bg-brand-warning" />
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
            <ColorSwatch label="brand.urgent" className="bg-brand-urgent" />
            <ColorSwatch label="brand.navy" className="bg-brand-navy" />
            <ColorSwatch label="brand.muted" className="bg-brand-muted" />
            <ColorSwatch
              label="brand.bg"
              className="bg-brand-bg border border-border"
              textClass="text-foreground"
            />
            <ColorSwatch
              label="brand.surface"
              className="bg-brand-surface border border-border"
              textClass="text-foreground"
            />
          </div>
        </Section>

        {/* Buttons */}
        <Section title="Buttons">
          <div className="flex flex-wrap gap-3">
            <Button variant="default">Primary / Blue</Button>
            <Button variant="secondary">Secondary / Indigo</Button>
            <Button variant="outline">Outline</Button>
            <Button variant="destructive">Destructive / Urgent</Button>
            <Button variant="ghost">Ghost</Button>
            <Button variant="link">Link</Button>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button size="sm">Small</Button>
            <Button size="default">Default</Button>
            <Button size="lg">Large</Button>
            <Button size="icon">
              <Bell className="h-4 w-4" />
            </Button>
            <Button disabled>Disabled</Button>
          </div>
        </Section>

        {/* Badges */}
        <Section title="Badges">
          <div className="flex flex-wrap gap-3">
            <Badge variant="default">Default</Badge>
            <Badge variant="secondary">Secondary</Badge>
            <Badge variant="outline">Outline</Badge>
            <Badge variant="success">
              <CheckCircle className="mr-1 h-3 w-3" />
              Success
            </Badge>
            <Badge variant="warning">
              <AlertTriangle className="mr-1 h-3 w-3" />
              Warning
            </Badge>
            <Badge variant="urgent">
              <AlertCircle className="mr-1 h-3 w-3" />
              Urgent
            </Badge>
            <Badge variant="destructive">Destructive</Badge>
          </div>
        </Section>

        {/* Cards */}
        <Section title="Card">
          <div className="grid gap-4 sm:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Work Order #1042</CardTitle>
                <CardDescription>HVAC Maintenance — 14 Oak Street</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">
                  Scheduled for Jun 2, 2026 at 9:00 AM. Technician: Marcus Reid.
                </p>
              </CardContent>
              <CardFooter className="gap-2">
                <Button size="sm">View Details</Button>
                <Button size="sm" variant="outline">
                  Reassign
                </Button>
              </CardFooter>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Customer: Northside Realty</CardTitle>
                <CardDescription>Account since Jan 2025</CardDescription>
              </CardHeader>
              <CardContent className="space-y-1">
                <p className="text-sm">
                  <span className="font-medium">Open jobs:</span> 3
                </p>
                <p className="text-sm">
                  <span className="font-medium">Balance due:</span> $1,240.00
                </p>
              </CardContent>
              <CardFooter>
                <Button size="sm" variant="ghost">
                  Open CRM
                </Button>
              </CardFooter>
            </Card>
          </div>
        </Section>

        {/* Inputs & Select */}
        <Section title="Form Controls — Input & Select">
          <Card>
            <CardContent className="pt-6 space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="customer-name">Customer Name</Label>
                  <Input id="customer-name" placeholder="e.g. John Smith" />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="job-type">Job Type</Label>
                  <Select>
                    <SelectTrigger id="job-type">
                      <SelectValue placeholder="Select a type…" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="hvac">HVAC</SelectItem>
                      <SelectItem value="plumbing">Plumbing</SelectItem>
                      <SelectItem value="electrical">Electrical</SelectItem>
                      <SelectItem value="cleaning">Cleaning</SelectItem>
                      <SelectItem value="locksmith">Locksmith</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="address">Service Address</Label>
                  <Input id="address" placeholder="123 Main St, City, State" />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="priority">Priority</Label>
                  <Select>
                    <SelectTrigger id="priority">
                      <SelectValue placeholder="Select priority…" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="low">Low</SelectItem>
                      <SelectItem value="normal">Normal</SelectItem>
                      <SelectItem value="high">High</SelectItem>
                      <SelectItem value="urgent">Urgent</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardContent>
          </Card>
        </Section>

        {/* Dialog */}
        <Section title="Dialog">
          <div className="flex gap-3">
            <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
              <DialogTrigger asChild>
                <Button variant="outline">Open Dialog</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Confirm Dispatch</DialogTitle>
                  <DialogDescription>
                    Send Marcus Reid to 14 Oak Street for the HVAC maintenance job? The technician
                    will be notified via SMS.
                  </DialogDescription>
                </DialogHeader>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setDialogOpen(false)}>
                    Cancel
                  </Button>
                  <Button
                    onClick={() => {
                      setDialogOpen(false);
                      toast.success('Technician dispatched', {
                        description: 'Marcus Reid has been notified via SMS.',
                      });
                    }}
                  >
                    Confirm Dispatch
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          </div>
        </Section>

        {/* Toast / Sonner */}
        <Section title="Toast Notifications — Sonner">
          <div className="flex flex-wrap gap-3">
            <Button
              variant="default"
              onClick={() =>
                toast.success('Work order completed', {
                  description: 'Job #1042 marked as done.',
                })
              }
            >
              <CheckCircle className="mr-2 h-4 w-4" />
              Success Toast
            </Button>
            <Button
              variant="outline"
              onClick={() =>
                toast('Reminder scheduled', {
                  description: 'Follow-up call set for tomorrow at 10 AM.',
                  icon: <Info className="h-4 w-4 text-brand-blue" />,
                })
              }
            >
              Info Toast
            </Button>
            <Button
              variant="destructive"
              onClick={() =>
                toast.error('Payment failed', {
                  description: 'Card ending in 4242 was declined.',
                })
              }
            >
              Error Toast
            </Button>
            <Button
              variant="secondary"
              onClick={() =>
                toast.warning('Overdue job detected', {
                  description: 'Job #1038 is 2 days past its due date.',
                })
              }
            >
              Warning Toast
            </Button>
          </div>
        </Section>
      </main>
    </div>
  );
}
