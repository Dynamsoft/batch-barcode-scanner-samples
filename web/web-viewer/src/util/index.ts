import type { TableRowObject } from "./types";

export const ANNOTATED_SUFFIX = "_AnnotatedImage";
export const ORIGINAL_SUFFIX = "_OriginalImage";

// getFileNameWithoutSuffix
export const noExt = (fileName: string) => {
  const index = fileName.lastIndexOf(".");
  return index === -1 ? fileName : fileName.substring(0, index);
}

export const stamp = (fileName: string) => {
  const regex = /_(\d{4})_(\d{1,2})_(\d{1,2})[ _](\d{1,2})[ _](\d{1,2})[ _](\d{1,2})[ _](\d{3,4})/;
  return fileName.match(regex)?.[0];
}

export const sortByTimestamp = (list: any[]) => {
  const regex = /_(\d{4})_(\d{1,2})_(\d{1,2})[ _](\d{1,2})[ _](\d{1,2})[ _](\d{1,2})[ _](\d{3,4})/;

  const getTimestamp = (name: string): number => {
    const match = name.match(regex);
    if (!match) {
      return 0;
    }

    const [_, year, month, day, hour, minute, second, millisecond] = match;

    const date = new Date(
      parseInt(year!),
      parseInt(month!) - 1,
      parseInt(day!),
      parseInt(hour!),
      parseInt(minute!),
      parseInt(second!),
      parseInt(millisecond!)
    );

    return date.getTime();
  };

  return list.sort((a, b) => {
    const timeA = getTimestamp(a.name);
    const timeB = getTimestamp(b.name);
    return timeB - timeA;
  });
}

export const sortAndGroupByTimestamp = <T extends { name: string }>(list: T[]) => {
  const sortedList = sortByTimestamp([...list]) as T[];
  const groupedList: T[][] = [];

  sortedList.forEach((item) => {
    const currentTimestamp = stamp(item.name) || item.name;
    const lastGroup = groupedList[groupedList.length - 1];
    const lastGroupFirstItem = lastGroup?.[0];
    const lastGroupTimestamp = lastGroupFirstItem ? (stamp(lastGroupFirstItem.name) || lastGroupFirstItem.name) : null;

    if (!lastGroup || lastGroupTimestamp !== currentTimestamp) {
      groupedList.push([item]);
      return;
    }

    lastGroup.push(item);
  });

  return groupedList;
}

export const tableRowsToObjects = (data: string[][]): TableRowObject[] => {
  if (!Array.isArray(data) || data.length <= 1) return [];

  const headers = data[0];
  const colCount = headers!.length;
  const rowCount = data.length - 1;

  const result = new Array<TableRowObject>(rowCount);

  for (let i = 1; i < data.length; i++) {
    const row = data[i] || [];
    const obj: TableRowObject = {};

    for (let j = 0; j < colCount; j++) {
      const key = headers![j];
      if (!key) continue;
      obj[key] = row[j] ?? "";
    }

    result[i - 1] = obj;
  }

  return result;
};