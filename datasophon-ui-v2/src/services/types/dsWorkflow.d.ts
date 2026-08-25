declare namespace DATASOPHON {
  interface DsPage<T> {
    list: T[];
    total: number;
    pageNo: number;
    pageSize: number;
  }

  interface DsProject {
    code: number;
    name: string;
    description?: string;
    owner?: string;
  }
}
