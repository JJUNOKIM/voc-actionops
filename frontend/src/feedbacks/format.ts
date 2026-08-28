export function formatScore(value: number | null): string {
  return value === null ? '-' : `${(value * 100).toFixed(1)}%`;
}

export function formatRating(value: number | null): string {
  return value === null ? '-' : `${value.toFixed(1)} / 5`;
}

export function feedbackDisplayId(externalId: string | null, id: number): string {
  return externalId === null ? `VOC #${id}` : externalId;
}
