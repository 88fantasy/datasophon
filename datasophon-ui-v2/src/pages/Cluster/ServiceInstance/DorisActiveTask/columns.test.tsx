import { describe, expect, it } from 'vitest';
import type { ColumnType } from 'antd/es/table';
import {
  ACTIVE_TASK_COLUMNS,
  displayValue,
  MISSING_VALUE,
  NOT_APPLICABLE_VALUE,
} from './columns';
import type { DorisActiveTask } from './types';

const query: DorisActiveTask = {
  taskId: 'q-1',
  type: 'QUERY',
  user: 'alice',
  clientAddress: '10.0.0.1:1234',
  sql: 'select 1',
  elapsedMs: 0,
  currentMemoryBytes: 0,
  peakMemoryBytes: 0,
  scanRows: 0,
  scanBytes: 0,
  cpuTimeMs: 0,
  workloadGroupId: 7,
  feHost: 'fe-1',
};

const load: DorisActiveTask = {
  taskId: 'load-1',
  type: 'LOAD',
  elapsedMs: 0,
  currentMemoryBytes: 0,
  peakMemoryBytes: 0,
  scanRows: 0,
  scanBytes: null,
  cpuTimeMs: 0,
};

const queryWithMissingClient: DorisActiveTask = {
  ...query,
  clientAddress: null,
};

function rendered(dataIndex: string, task: DorisActiveTask): unknown {
  const column = ACTIVE_TASK_COLUMNS.find(
    (candidate): candidate is ColumnType<DorisActiveTask> =>
      'dataIndex' in candidate && candidate.dataIndex === dataIndex,
  );
  if (!column?.render) throw new Error(`missing column ${dataIndex}`);
  return column.render(task[dataIndex as keyof DorisActiveTask], task, 0);
}

describe('Doris active task columns', () => {
  it('does not expose the removed database or progress columns', () => {
    const fields = ACTIVE_TASK_COLUMNS.flatMap((column) =>
      'dataIndex' in column ? [String(column.dataIndex)] : [],
    );
    const titles = ACTIVE_TASK_COLUMNS.map((column) => String(column.title));

    expect(fields).not.toContain('database');
    expect(fields).not.toContain('progress');
    expect(titles.join(' ')).not.toContain('数据库');
    expect(titles.join(' ')).not.toContain('进度');
  });

  it('keeps zero as a real value and distinguishes missing from not applicable', () => {
    expect(rendered('currentMemoryBytes', query)).toBe('0 B');
    expect(rendered('user', load)).toBe(NOT_APPLICABLE_VALUE);
    expect(rendered('scanBytes', load)).toBe(NOT_APPLICABLE_VALUE);
    expect(rendered('clientAddress', queryWithMissingClient)).toBe(
      MISSING_VALUE,
    );
    expect(displayValue(0)).toBe('0');
  });
});
