import { describe, expect, it } from 'vitest';

import {
  buildInitialMapping,
  compactMapping,
  csvFileError,
  mappingError,
  readCsvHeaders,
} from './csv-mapping';

describe('CSV column mapping', () => {
  it('reads a quoted header row and removes a UTF-8 BOM', async () => {
    const file = new File(
      ['\uFEFFreview_id,"review_text",score\nreview-1,"결제가 느려요",2'],
      'reviews.csv',
      { type: 'text/csv' },
    );

    await expect(readCsvHeaders(file)).resolves.toEqual(['review_id', 'review_text', 'score']);
  });

  it('rejects duplicate CSV headers', async () => {
    const file = new File(['content,content\nfirst,second'], 'duplicate.csv', {
      type: 'text/csv',
    });

    await expect(readCsvHeaders(file)).rejects.toThrow('중복된 컬럼명');
  });

  it('suggests known fields without assigning the same target twice', () => {
    const mapping = buildInitialMapping([
      'review_id',
      'review_text',
      'content',
      'score',
      'created_date',
    ]);

    expect(mapping).toEqual({
      review_id: 'external_id',
      review_text: 'content',
      content: '',
      score: 'rating',
      created_date: 'feedback_created_at',
    });
    expect(mappingError(mapping)).toBeNull();
    expect(compactMapping(mapping)).not.toHaveProperty('content');
  });

  it('requires one content mapping and validates the file before parsing', () => {
    expect(mappingError({ message: '', score: 'rating' })).toBe(
      '피드백 내용 컬럼을 반드시 매핑해 주세요.',
    );
    expect(csvFileError(new File(['value'], 'reviews.txt'))).toBe(
      'CSV 파일만 업로드할 수 있습니다.',
    );
    expect(csvFileError(new File([], 'empty.csv'))).toBe(
      '비어 있는 파일은 업로드할 수 없습니다.',
    );
  });
});
