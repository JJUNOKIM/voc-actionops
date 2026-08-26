import Papa from 'papaparse';

import type { ColumnMappingDraft, SystemField } from './types';

export const MAX_CSV_FILE_SIZE = 5 * 1024 * 1024;

const fieldAliases: Record<SystemField, string[]> = {
  external_id: ['externalid', 'reviewid', 'feedbackid', 'ticketid'],
  content: ['content', 'reviewtext', 'feedback', 'message', 'comment', 'body'],
  customer_segment: ['customersegment', 'segment', 'customertype', 'tier'],
  product_name: ['productname', 'product', 'service', 'channel'],
  rating: ['rating', 'score', 'stars', 'star'],
  language: ['language', 'lang', 'locale'],
  feedback_created_at: ['feedbackcreatedat', 'createdat', 'createddate', 'date', 'timestamp'],
};

export function csvFileError(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.csv')) {
    return 'CSV 파일만 업로드할 수 있습니다.';
  }
  if (file.size === 0) {
    return '비어 있는 파일은 업로드할 수 없습니다.';
  }
  if (file.size > MAX_CSV_FILE_SIZE) {
    return '파일 크기는 5MB 이하여야 합니다.';
  }
  return null;
}

export function readCsvHeaders(file: File): Promise<string[]> {
  return new Promise((resolve, reject) => {
    Papa.parse<string[]>(file, {
      preview: 1,
      skipEmptyLines: 'greedy',
      complete: (result) => {
        const fatalError = result.errors.find((error) => error.code !== 'UndetectableDelimiter');
        if (fatalError !== undefined) {
          reject(new Error('CSV 헤더를 읽을 수 없습니다. 파일 형식을 확인해 주세요.'));
          return;
        }

        const headers = (result.data[0] ?? []).map((header) =>
          String(header).replace(/^\uFEFF/, '').trim(),
        );
        if (headers.length === 0 || headers.some((header) => header === '')) {
          reject(new Error('CSV 첫 행에 비어 있는 컬럼명이 있습니다.'));
          return;
        }
        if (new Set(headers).size !== headers.length) {
          reject(new Error('CSV 첫 행에 중복된 컬럼명이 있습니다.'));
          return;
        }
        resolve(headers);
      },
      error: () => reject(new Error('CSV 파일을 읽을 수 없습니다.')),
    });
  });
}

export function buildInitialMapping(headers: string[]): ColumnMappingDraft {
  const usedFields = new Set<SystemField>();
  return Object.fromEntries(
    headers.map((header) => {
      const normalizedHeader = normalizeHeader(header);
      const matchedField = (Object.entries(fieldAliases) as Array<[SystemField, string[]]>).find(
        ([field, aliases]) => !usedFields.has(field) && aliases.includes(normalizedHeader),
      )?.[0];
      if (matchedField !== undefined) {
        usedFields.add(matchedField);
      }
      return [header, matchedField ?? ''];
    }),
  );
}

export function mappingError(mapping: ColumnMappingDraft): string | null {
  const selectedFields = Object.values(mapping).filter(
    (field): field is SystemField => field !== '',
  );
  if (!selectedFields.includes('content')) {
    return '피드백 내용 컬럼을 반드시 매핑해 주세요.';
  }
  if (new Set(selectedFields).size !== selectedFields.length) {
    return '같은 시스템 필드를 두 번 매핑할 수 없습니다.';
  }
  return null;
}

export function compactMapping(mapping: ColumnMappingDraft): Record<string, SystemField> {
  return Object.fromEntries(
    Object.entries(mapping).filter(
      (entry): entry is [string, SystemField] => entry[1] !== '',
    ),
  );
}

function normalizeHeader(header: string): string {
  return header.toLowerCase().replace(/[^a-z0-9]/g, '');
}
