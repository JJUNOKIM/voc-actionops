import { ChevronDown, ChevronUp, ChevronsUp, Minus } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

import type { IssuePriority } from './types';

const priorityIcons: Record<IssuePriority, LucideIcon> = {
  P0: ChevronsUp,
  P1: ChevronUp,
  P2: Minus,
  P3: ChevronDown,
};

export function PriorityBadge({ priority }: { priority: IssuePriority }) {
  const Icon = priorityIcons[priority];

  return (
    <span className={`priority-badge priority-badge--${priority.toLowerCase()}`}>
      <Icon size={12} strokeWidth={2.5} aria-hidden="true" />
      <span>{priority}</span>
    </span>
  );
}
