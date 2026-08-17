import oneDrive from "../tokenManagers/oneDrive";
import loginManager from "./loginManager";
import { useDialogStore } from "../store/dialog";
import type { DriveDeltaResult, ListFilesOptions, ListFilesResult } from "./types";

class StorageResourceService {
  // example path: ":/Attachments:/"
  public async getFileList(options: ListFilesOptions = {}): Promise<ListFilesResult> {
    try {
      if (loginManager.activeLoginMethod === "OneDrive") {
        return await oneDrive.getFileList(options);
      }
    } catch (ex: any) {
      this._handleError(ex);
    }
    return { items: [], nextLink: null };
  }

  public async getDriveDelta(deltaLink?: string): Promise<DriveDeltaResult> {
    try {
      if (loginManager.activeLoginMethod === "OneDrive") {
        return await oneDrive.getDriveDelta(deltaLink);
      }
    } catch (ex: any) {
      this._handleError(ex);
    }

    return {
      items: [],
      deltaLink: null,
    };
  }

  public async getFileBinary(itemId?: string, forceRefresh: boolean = false, controller?: AbortController) {
    try {
      if (itemId?.startsWith("sample:")) {
        const response = await fetch(itemId.slice("sample:".length), {
          cache: forceRefresh ? "reload" : "default",
          signal: controller?.signal,
        });
        if (!response.ok) {
          throw new Error(`Failed to load sample file: ${response.status}`);
        }
        return await response.blob();
      }

      if (loginManager.activeLoginMethod === "OneDrive") {
        return await oneDrive.getFileBinary(itemId, forceRefresh, controller);
      }
    } catch (ex: any) {
      this._handleError(ex);
    }
  }

  public async getFileBinaryByName(fileName: string, folderName: string = "", controller?: AbortController) {
    try {
      if (loginManager.activeLoginMethod === "OneDrive") {
        return await oneDrive.getFileBinaryByName(fileName, folderName, controller);
      }
    } catch (ex: any) {
      this._handleError(ex);
    }
  }

  public async getThumbnails(itemId: string) {
    try {
      if (loginManager.activeLoginMethod === "OneDrive") {
        return await oneDrive.getThumbnails(itemId);
      }
    } catch (ex: any) {
      this._handleError(ex);
    }
  }

  public async deleteFile(items: any | any[]) {
    try {
      if (loginManager.activeLoginMethod === "OneDrive") {
        if (!Array.isArray(items)) {
          items = [items];
        }
        for (let item of items) {
          await oneDrive.deleteFile(item.id);
        }
      }
    } catch (ex: any) {
      this._handleError(ex);
    }
  }

  private _handleError(ex: any) {
    if (ex.message === "No access token available.") {
      const reAuthorizeDialogStore = useDialogStore();
      reAuthorizeDialogStore.setDialogVisible({ reAuthDialog: true });
    } else {
      throw ex;
    }
  }
}

const storageResourceService = new StorageResourceService();

export default storageResourceService;
