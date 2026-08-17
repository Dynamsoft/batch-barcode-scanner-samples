import type { AuthenticationResult } from "@azure/msal-browser";
import type { ANNOTATED_SUFFIX, ORIGINAL_SUFFIX } from ".";

export type LoginMethod = "OneDrive" | "Google Drive" | "Dropbox" | "SharePoint" | "Box" | "";

export type LoginMethodMeta = { name: LoginMethod, isAvailable: boolean, icon: string; isActive: boolean, tokenRequest: any };

export type ListFilesOptions = {
  pageSize?: number;
  nextLink?: string;
  folderName?: string;
};

export type ListFilesResult = {
  items: any[];
  nextLink: string | null;
};

export type DriveDeltaResult = {
  items: any[];
  deltaLink: string | null;
};

export type TableRowObject = Record<string, string>;

export type OneDriveAuthInfo = AuthenticationResult & LoginMethodMeta;

export type OriginalOrAnnotated = typeof ANNOTATED_SUFFIX | typeof ORIGINAL_SUFFIX;