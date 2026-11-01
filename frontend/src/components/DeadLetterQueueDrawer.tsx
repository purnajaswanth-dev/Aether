import React, { useState, useEffect } from "react";
import { DeadLetterEvent } from "@/types";
import { api } from "@/services/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { CodeBlock } from "@/components/ui/code-block";
import { EmptyState } from "@/components/ui/empty-state";
import { ShieldAlert, RefreshCw, Trash2, CheckCircle2, Clock, AlertTriangle, Eye } from "lucide-react";

interface DeadLetterQueueDrawerProps {
  projectId: string;
}

export function DeadLetterQueueDrawer({ projectId }: DeadLetterQueueDrawerProps) {
  const [events, setEvents] = useState<DeadLetterEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedEvent, setSelectedEvent] = useState<DeadLetterEvent | null>(null);
  const [retryingId, setRetryingId] = useState<string | null>(null);

  const loadEvents = async () => {
    try {
      setLoading(true);
      const data = await api.listDeadLetterEvents(projectId);
      setEvents(data);
    } catch (err) {
      console.error("Failed to load DLQ events", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadEvents();
  }, [projectId]);

  const handleRetry = async (eventId: string) => {
    try {
      setRetryingId(eventId);
      await api.retryDeadLetterEvent(projectId, eventId);
      await loadEvents();
      if (selectedEvent?.id === eventId) setSelectedEvent(null);
    } catch (err: any) {
      alert("Retry failed: " + (err.message || "Unknown error"));
    } finally {
      setRetryingId(null);
    }
  };

  const handleDiscard = async (eventId: string) => {
    if (!confirm("Are you sure you want to discard this dead-letter event?")) return;
    try {
      await api.discardDeadLetterEvent(projectId, eventId);
      await loadEvents();
      if (selectedEvent?.id === eventId) setSelectedEvent(null);
    } catch (err: any) {
      alert("Discard failed: " + (err.message || "Unknown error"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-text-primary">Dead-Letter Queue (DLQ)</h2>
          <p className="text-xs text-text-secondary">
            Inspect failed webhook deliveries and external synchronization events with manual replay capability.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={loadEvents} className="gap-1.5 text-xs">
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-20 rounded-panel bg-subtle animate-pulse border border-border" />
          ))}
        </div>
      ) : events.length === 0 ? (
        <EmptyState
          title="Dead-Letter Queue is empty"
          description="All webhook events and background synchronization batches processed successfully."
          icon={CheckCircle2}
        />
      ) : (
        <div className="divide-y divide-border rounded-panel border border-border bg-canvas overflow-hidden">
          {events.map((evt) => (
            <div
              key={evt.id}
              className="p-4 hover:bg-subtle/50 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3"
            >
              <div className="space-y-1 min-w-0">
                <div className="flex items-center gap-2">
                  <Badge variant="danger" size="sm" showDot>
                    {evt.status}
                  </Badge>
                  <span className="text-xs font-mono font-semibold text-text-primary uppercase">
                    {evt.provider} / {evt.eventType}
                  </span>
                  <span className="text-[10px] text-text-tertiary">
                    Attempts: <strong>{evt.attempts}</strong>
                  </span>
                </div>
                <p className="text-xs text-destructive font-mono truncate max-w-xl">
                  {evt.errorMessage}
                </p>
                <div className="flex items-center gap-2 text-[11px] text-text-tertiary">
                  <Clock className="h-3 w-3" />
                  Received: {new Date(evt.createdAt).toLocaleString()}
                </div>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-1 text-xs"
                  onClick={() => setSelectedEvent(evt)}
                >
                  <Eye className="h-3.5 w-3.5" />
                  Inspect
                </Button>
                <Button
                  variant="default"
                  size="sm"
                  className="gap-1 text-xs"
                  onClick={() => handleRetry(evt.id)}
                  disabled={retryingId === evt.id}
                >
                  <RefreshCw className={`h-3.5 w-3.5 ${retryingId === evt.id ? "animate-spin" : ""}`} />
                  Retry
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="text-xs text-destructive hover:bg-destructive/10 border-destructive/30"
                  onClick={() => handleDiscard(evt.id)}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Inspect Dialog */}
      <Dialog open={Boolean(selectedEvent)} onOpenChange={(open) => !open && setSelectedEvent(null)}>
        <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col p-6">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold flex items-center gap-2">
              <ShieldAlert className="h-4 w-4 text-destructive" />
              Dead-Letter Event: {selectedEvent?.provider} ({selectedEvent?.eventType})
            </DialogTitle>
            <DialogDescription className="text-xs text-text-secondary">
              Review error message, HTTP headers, and raw event payload before replaying.
            </DialogDescription>
          </DialogHeader>

          {selectedEvent && (
            <div className="space-y-4 py-2 overflow-auto flex-1">
              <div>
                <label className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wider block mb-1">
                  Failure Reason
                </label>
                <div className="rounded p-2 bg-destructive/10 text-destructive text-xs font-mono border border-destructive/20">
                  {selectedEvent.errorMessage}
                </div>
              </div>

              <div>
                <label className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wider block mb-1">
                  Request Headers
                </label>
                <CodeBlock code={JSON.stringify(selectedEvent.headers, null, 2)} language="json" />
              </div>

              <div>
                <label className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wider block mb-1">
                  Raw Payload
                </label>
                <CodeBlock code={selectedEvent.payload} language="json" />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-border">
                <Button variant="outline" size="sm" onClick={() => setSelectedEvent(null)}>
                  Close
                </Button>
                <Button
                  size="sm"
                  onClick={() => handleRetry(selectedEvent.id)}
                  disabled={retryingId === selectedEvent.id}
                  className="gap-1.5"
                >
                  <RefreshCw className={`h-3.5 w-3.5 ${retryingId === selectedEvent.id ? "animate-spin" : ""}`} />
                  Retry Event
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
