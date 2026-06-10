import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/entities/user/model/authStore';
import { useAlerts, useDismissAlert } from '@/entities/alert/api/alertsApi';

/**
 * Renders a red escalation banner for each pending JOB_NOT_HANDLED alert.
 * Only visible to ADMIN users. Polls every 30 s via useAlerts (PRD §28, TASK-118).
 */
export default function AlertBanner() {
  const user = useAuthStore((s) => s.user);
  const { data: alerts } = useAlerts();
  const dismiss = useDismissAlert();
  const navigate = useNavigate();

  if (user?.role !== 'ADMIN') return null;
  if (!alerts || alerts.length === 0) return null;

  return (
    <div role="alert" aria-live="assertive">
      {alerts.map((alert) => (
        <div
          key={alert.id}
          style={{ backgroundColor: '#C62828' }}
          className="flex items-center justify-between px-4 py-2 text-white text-sm font-medium"
        >
          <span>
            JOB NOT HANDLED &mdash; Job {alert.jobId ? alert.jobId.slice(0, 8) : 'unknown'}
          </span>
          <div className="flex items-center gap-2 ml-4 shrink-0">
            <button
              onClick={() => void navigate(`/jobs/${alert.jobId}`)}
              className="px-3 py-1 rounded bg-white/20 hover:bg-white/30 transition-colors text-white text-xs font-semibold"
            >
              View Job
            </button>
            <button
              onClick={() => dismiss.mutate(alert.id)}
              disabled={dismiss.isPending}
              className="px-3 py-1 rounded bg-white/10 hover:bg-white/20 transition-colors text-white text-xs font-semibold disabled:opacity-50"
            >
              Dismiss
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
