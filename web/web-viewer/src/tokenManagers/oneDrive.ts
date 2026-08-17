import { reactive } from "vue";
import { PublicClientApplication } from "@azure/msal-browser";
import type { DriveDeltaResult, ListFilesOptions, ListFilesResult, OneDriveAuthInfo } from "../util/types";
import defaultUserPhoto from "../assets/image/default-user-photo.jpeg";
import methodList from "../util/methodList";
import { ElMessage } from "element-plus";

const ROOT_FOLDER = ":/BBS";
const MY_NAME = "OneDrive";

type BinaryCacheEntry = {
  promise: Promise<Blob>;
  controller: AbortController;
};

const encodeFolderPath = (folderName: string = "") => {
  if (!folderName) return "";
  return `/${folderName
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/")}`;
};

// MSAL Configuration
const msalConfig = {
  auth: {
    clientId: import.meta.env.DYNAMSOFT_ONEDRIVE_CLIENT_ID, // Replace with your Application ID
    authority: "https://login.microsoftonline.com/common",
    redirectUri: import.meta.env.DYNAMSOFT_ONEDRIVE_REDIRECT_URI,
  },
  cache: {
    cacheLocation: "localStorage",
    storeAuthStateInCookie: false,
  },
};

const loginRequest = {
  scopes: ["User.Read", "Files.ReadWrite"],
  prompt: "select_account",
};

const storedMethodList = localStorage.getItem("BBS_Web_Viewer_LoggedIn_Method_List");
class OneDriveTokenManager {
  public myMSALObj: PublicClientApplication | null = null;
  public authInfo: OneDriveAuthInfo | null;
  public tokenRequest: any;
  public isLoggedIn: boolean;
  public avatarUrl: string;
  public binaryFileMap = new Map<string, BinaryCacheEntry>();

  private pMSALInit: Promise<void> | null = null;

  constructor() {
    const oneDriveLoggedInfos = storedMethodList ? JSON.parse(storedMethodList).filter((method: any) => method.name === MY_NAME) : [];
    this.authInfo = oneDriveLoggedInfos.length > 0 ? oneDriveLoggedInfos.find((info: any) => info.isActive && !this.isTokenExpired(info)) : null;
    this.isLoggedIn = !!this.authInfo;
    this.avatarUrl = defaultUserPhoto;

    if (this.isLoggedIn) {
      this.tokenRequest = {
        scopes: ["User.Read", "Files.ReadWrite"],
        account: this.authInfo?.account,
      };
      this.myMSALObj = new PublicClientApplication(msalConfig);
      this.pMSALInit = this.myMSALObj.initialize(); // Required in MSAL 3.x
    }

    if (storedMethodList) {
      const parsedMethodList = JSON.parse(storedMethodList);
      const oneDriveIndex = parsedMethodList.findIndex((target: any) => target.name === MY_NAME);
      if (oneDriveIndex !== -1 && !this.isLoggedIn) {
        parsedMethodList.splice(oneDriveIndex, 1);
      }
      localStorage.setItem("BBS_Web_Viewer_LoggedIn_Method_List", JSON.stringify(parsedMethodList));
    }
  }

  public isTokenExpired(authInfo: OneDriveAuthInfo | null): boolean {
    let isExpired = false;
    const currentTimestamp = Date.now();
    if (authInfo?.expiresOn) {
      const expiryTimestamp = new Date(authInfo.expiresOn).getTime();
      isExpired = currentTimestamp >= expiryTimestamp;
    }
    return isExpired;
  }

  public async login() {
    this.myMSALObj ??= new PublicClientApplication(msalConfig);
    this.pMSALInit = this.myMSALObj.initialize(); // Required in MSAL 3.x
    await this.pMSALInit;

    // if (!this.isTokenExpired(this.authInfo)) {
    //   const avatarUrl = await this.getUserAvatar();
    //   this.avatarUrl = avatarUrl;
    // }
    // 1. Sign In and Get Account
    const loginResponse = await this.myMSALObj.loginPopup(loginRequest);

    if (this.authInfo?.account?.homeAccountId === loginResponse.account.homeAccountId) {
      ElMessage({
        message: "You have already logged in with this account.",
        type: "warning",
        duration: 3000,
        customClass: "custom-dynamsoft-message",
      });
      return;
    }

    // 2. Get Access Token Silently
    this.tokenRequest = {
      scopes: ["User.Read", "Files.ReadWrite"],
      account: loginResponse.account,
    };

    const tokenResponse = await this.myMSALObj.acquireTokenSilent(this.tokenRequest);
    const oneDriveMethod = methodList.find((m) => m.name === MY_NAME)!;
    const authInfo: OneDriveAuthInfo = {
      ...tokenResponse,
      tokenRequest: this.tokenRequest,
      isAvailable: oneDriveMethod.isAvailable,
      icon: oneDriveMethod.icon,
      name: oneDriveMethod.name,
      isActive: true,
    };
    await this.setToken(authInfo);
  }

  public async setToken(authInfo: OneDriveAuthInfo) {
    this.isLoggedIn = true;
    this.authInfo = authInfo;
    //this.avatarUrl = await this.getUserAvatar();
  }

  public async getToken() {
    try {
      await this.pMSALInit;
      const tokenResponse = await this.myMSALObj!.acquireTokenSilent(this.authInfo?.tokenRequest);
      const oneDriveMethod = methodList.find((m) => m.name === MY_NAME)!;
      const authInfo: OneDriveAuthInfo = {
        ...tokenResponse,
        tokenRequest: this.authInfo?.tokenRequest,
        isAvailable: oneDriveMethod.isAvailable,
        icon: oneDriveMethod.icon,
        name: oneDriveMethod.name,
        isActive: true,
      };
      await this.setToken(authInfo);
    } catch (ex) {
      throw new Error("No access token available.");
    }
    return this.authInfo?.accessToken;
  }

  public removeToken() {
    this.authInfo = null;
    this.isLoggedIn = false;
    this.avatarUrl = defaultUserPhoto;
  }

  public async getFileList(options: ListFilesOptions = {}): Promise<ListFilesResult> {
    const { pageSize = 0, nextLink, folderName = "" } = options;
    const encodedFolderPath = encodeFolderPath(folderName);
    const url = nextLink ?? `https://graph.microsoft.com/v1.0/me/drive/root${ROOT_FOLDER}${encodedFolderPath}:/children?$top=${pageSize}`;

    const token = await this.getToken();
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (response.status === 404) {
      return { items: [], nextLink: null };
    }

    if (!response.ok) {
      throw new Error(`Failed to list files: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    return {
      items: Array.isArray(data.value) ? data.value : [],
      nextLink: data["@odata.nextLink"] ?? null,
    };
  }

  public async getDriveDelta(deltaLink?: string): Promise<DriveDeltaResult> {
    const token = await this.getToken();
    const url = deltaLink ?? `https://graph.microsoft.com/v1.0/me/drive/root${ROOT_FOLDER}:/delta?$select=id,name,parentReference,folder,file,deleted`;
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (response.status === 404) {
      return { items: [], deltaLink: null };
    }

    if (!response.ok) {
      throw new Error(`Failed to get drive delta: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    return {
      items: Array.isArray(data.value) ? data.value : [],
      deltaLink: data["@odata.deltaLink"] ?? null,
    };
  }

  public async getFileBinary(itemId?: string, forceRefresh: boolean = false, controller?: AbortController): Promise<Blob> {
    if (!itemId) return Promise.reject("itemId is required.");
    if (forceRefresh) {
      this.binaryFileMap.delete(itemId);
    }
    let cacheItem = this.binaryFileMap.get(itemId);
    if (cacheItem && cacheItem.controller.signal.aborted) {
      this.binaryFileMap.delete(itemId);
      cacheItem = undefined;
    }

    if (cacheItem && !forceRefresh) {
      return await cacheItem.promise;
    }

    const getFileBinaryPromise = (async () => {
      const token = await this.getToken();
      const response = await fetch(`https://graph.microsoft.com/v1.0/me/drive/items/${itemId}/content`, {
        headers: { Authorization: `Bearer ${token}` },
        signal: controller?.signal,
      });
      if (!response.ok) {
        const text = await response.text();
        throw new Error(`getFileBinary failed: ${response.status} ${response.statusText} - ${text}`);
      }
      const blob = await response.blob();
      return blob;
    })();
    this.binaryFileMap.set(itemId, { promise: getFileBinaryPromise, controller: controller ?? new AbortController() });
    getFileBinaryPromise.catch(() => {
      this.binaryFileMap.delete(itemId);
    });

    return await getFileBinaryPromise;
  }

  public async getFileBinaryByName(fileName: string, folderName: string = "", controller?: AbortController): Promise<Blob> {
    const getFileBinaryByNamePromise = (async () => {
      const token = await this.getToken();
      const encodedFolderPath = encodeFolderPath(folderName);
      const response = await fetch(`https://graph.microsoft.com/v1.0/me/drive/root${ROOT_FOLDER}${encodedFolderPath}/${encodeURIComponent(fileName)}:/content`, {
        headers: { Authorization: `Bearer ${token}` },
        signal: controller?.signal,
      });
      if (!response.ok) {
        const text = await response.text();
        throw new Error(`getFileBinaryByName failed: ${response.status} ${response.statusText} - ${text}`);
      }
      const blob = await response.blob();
      return blob;
    })();

    const blob = await getFileBinaryByNamePromise;
    return blob;
  }

  public async deleteFile(itemId: string): Promise<void> {
    const token = await this.getToken();
    const response = await fetch(`https://graph.microsoft.com/v1.0/me/drive/items/${itemId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Failed to delete file: ${response.status} ${response.statusText} - ${text}`);
    }

    this.binaryFileMap.delete(itemId);
  }

  public async getThumbnails(itemId: string) {
    const token = await this.getToken();
    const response = await fetch(`https://graph.microsoft.com/v1.0/me/drive/items/${itemId}?$expand=thumbnails`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const json = await response.json();
    const thumbnailUrl = json.thumbnails[0].small.url;

    const response2 = await fetch(thumbnailUrl);
    const thumbnailsBlob = await response2.blob();
    return URL.createObjectURL(thumbnailsBlob);
  }

  public async getUserAvatar() {
    if (!this.authInfo?.accessToken) return defaultUserPhoto;
    try {
      const response = await fetch("https://graph.microsoft.com/v1.0/me/photo/$value", {
        headers: {
          Authorization: `Bearer ${this.authInfo.accessToken}`,
        },
      });

      if (!response.ok) {
        if (response.status === 404) {
          console.warn("User has not set a profile picture.");
          return defaultUserPhoto;
        }
        throw new Error(`Error fetching photo: ${response.statusText}`);
      }

      const photoBlob = await response.blob();
      const photoUrl = URL.createObjectURL(photoBlob);
      return photoUrl;
    } catch (error) {
      console.error("Failed to fetch user photo", error);
      return defaultUserPhoto;
    }
  }

  public async logout() {
    this.removeToken();
  }
}

const oneDriveTokenManager = new OneDriveTokenManager();

export default reactive(oneDriveTokenManager);
