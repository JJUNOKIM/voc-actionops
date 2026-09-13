import { ArrowDown, ArrowUp, Minus, OctagonAlert } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

import type { IssuePriority } from './types';

const priorityIcons: Record<IssuePriority, LucideIcon> = {
  P0: OctagonAlert,
  P1: ArrowUp,
  P2: Minus,
  P3: ArrowDown,
};

export function PriorityBadge({ priority }: { priority: IssuePriority }) {
  const Icon = priorityIcons[priority];

  return (
    <span className={`priority-badge priority-badge--${priority.toLowerCase()}`}>
      <Icon size={11} strokeWidth={2.4} aria-hidden="true" />
      <span>{priority}</span>
    </span>
  );
}
