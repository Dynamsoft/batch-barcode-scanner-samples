<template>
  <div class="dashboard-header">
    <a class="left" href="https://www.dynamsoft.com/" target="_blank">
      <img src="../../assets/image/dynamsoft-logo.png" alt="dynamsoft-logo" class="dynamsoft-logo" />
    </a>
    <div class="right">
      <div class="title">
        <div class="title-part-1">Batch Barcode Scanner</div>
        <div class="title-part-2">Web Viewer</div>
      </div>
      <div class="refresh-and-account-bar" v-if="loginManager.isLoggedIn">
        <div class="refresh-files" @click="refreshFiles">
          <div v-html="NOTIFICATION_DOT" v-show="isShowNotificationDot" class="svg-icon flex-center"></div>
          <span>Refresh Files</span>
        </div>
        <div class="user-info" @click="isShowAccountSelector = !isShowAccountSelector">
          <div class="login-method-icon" v-html="currentLoginMethodSvg?.icon"></div>
          <div class="account-info">
            <span class="service-name">{{ serviceName }}</span>
            <span class="user-email">{{ userEmail }}</span>
          </div>
        </div>
      </div>
      <button class="to-login-page-btn" @click="toLoginPage" v-else>Authorize Your Cloud Storage</button>
      <div class="storage-account-selector" v-show="isShowAccountSelector">
        <ul class="logged-in-list">
          <li v-for="account in loginManager.loggedInAccounts" @click="switchAccount(account)">
            <div class="account-item">
              <div class="list-icon" v-html="(account as any).icon"></div>
              <div class="account-info">
                <span class="service-name">{{ account.name }}</span>
                <span class="user-email">{{ (account as any).account.username }}</span>
              </div>
              <div v-html="CHECK_MARK" class="check-mark" v-show="account.isActive"></div>
            </div>
          </li>
        </ul>
        <div class="connect-another-storage" @click="connectAnotherStorage">
          <div v-html="PLUS_SIGN" class="svg-icon flex-center"></div>
          <span>Connect another storage</span>
        </div>
        <div class="sign-out" @click="logout">
          <div v-html="SIGN_OUT" class="svg-icon flex-center"></div>
          <span>Sign out</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { useFileListStore } from "../../store/fileList";
import { sortByTimestamp } from "../../util";
import loginManager from "../../util/loginManager";
import storageResourceService from "../../util/storageResourceService";
import methodList from "../../util/methodList";
import { ElLoading } from "element-plus";
import type { LoginMethodMeta } from "../../util/types";
import { CHECK_MARK, NOTIFICATION_DOT, PLUS_SIGN, SIGN_OUT } from "../../icons";

const fileListStore = useFileListStore();
const isShowAccountSelector = ref(false);
const isShowNotificationDot = ref(false);
const driveDeltaLink = ref("");
const ROOT_FOLDER_PATH = "/drive/root:/BBS";
const pendingNewFiles = ref<any[]>([]);
const seenItemIds = new Set<string>();
let intervalId: ReturnType<typeof setInterval>;

const props = defineProps<{
  updateLoginMethodPanelVisibility: (visible: boolean) => void;
}>();

const currentLoginMethodSvg = computed(() => {
  return methodList.filter((method) => {
    return method.name === loginManager.activeLoginMethod;
  })[0];
});

const serviceName = computed(() => {
  const target = loginManager.loggedInAccounts.find((account) => account.isActive);
  return target?.name;
});

const userEmail = computed(() => {
  const target = loginManager.loggedInAccounts.find((account) => account.isActive) as any;
  return target.account.username;
});

const connectAnotherStorage = () => {
  props.updateLoginMethodPanelVisibility(true);
};

const toLoginPage = () => {
  loginManager.isShowLoginPage = true;
  localStorage.setItem("BBS_Web_Viewer_First_Screen", "Login");
};

const syncSeenItemIds = (items: any[]) => {
  for (const item of items) {
    if (item.deleted) {
      seenItemIds.delete(item.id);
      continue;
    }
    seenItemIds.add(item.id);
  }
};

const hasNewItems = (items: any[]) => {
  return items.some((item: any) => !item.deleted && !seenItemIds.has(item.id));
};

const getNewTimestampedFiles = (items: any[]) => {
  return items.filter((item: any) => !item.deleted && !item.folder && !seenItemIds.has(item.id));
};

const getRelativeFolderPath = (parentPath?: string) => {
  if (!parentPath || !parentPath.startsWith(ROOT_FOLDER_PATH)) {
    return "";
  }
  return parentPath.slice(ROOT_FOLDER_PATH.length).replace(/^\//, "");
};

const getDriveSnapshot = async () => {
  seenItemIds.clear();

  const deltaResult = await storageResourceService.getDriveDelta();
  syncSeenItemIds(deltaResult.items);
  driveDeltaLink.value = deltaResult.deltaLink ?? "";
  return deltaResult.items;
};

const refreshFiles = async () => {
  try {
    clearInterval(intervalId);
    fileListStore.setListLoading(true);
    isShowNotificationDot.value = false;
    await checkFileUpdate();
    const latestFile = pendingNewFiles.value.length > 0 ? pendingNewFiles.value[0] : null;
    const targetFolderPath = getRelativeFolderPath(latestFile?.parentReference?.path);
    await getDriveSnapshot();
    const { items: targetFolderItems } = await storageResourceService.getFileList({ folderName: targetFolderPath || undefined });

    fileListStore.setFileList(sortByTimestamp(targetFolderItems));
    const firstFile = fileListStore.list.find((item: any) => !item.folder);
    fileListStore.setSelectedFileName(firstFile?.name || "");
    if (firstFile) {
      fileListStore.markViewed(firstFile);
    }
    fileListStore.setActiveFolderPath(targetFolderPath);
    pendingNewFiles.value = [];

    // After refreshing, the file name may remain unchanged while the file content is updated.
    // Use `triggerRefresh` to ensure the latest file content is re-fetched and re-rendered.
    fileListStore.triggerRefresh();
  } finally {
    fileListStore.setListLoading(false);
    startFileChangeDetection(false);
  }
};

const checkFileUpdate = async () => {
  const deltaResult = await storageResourceService.getDriveDelta(driveDeltaLink.value || undefined);
  const newFiles = getNewTimestampedFiles(deltaResult.items);
  const hasAddedItems = hasNewItems(deltaResult.items);
  const hasNewFiles = newFiles.length > 0;
  if (newFiles.length > 0) {
    pendingNewFiles.value = sortByTimestamp(newFiles);
  }
  syncSeenItemIds(deltaResult.items);
  driveDeltaLink.value = deltaResult.deltaLink ?? driveDeltaLink.value;
  return {
    hasAddedItems,
    hasNewFiles,
  };
};

const startFileChangeDetection = async (shouldRefreshSnapshot: boolean = true) => {
  clearInterval(intervalId);
  if (shouldRefreshSnapshot || !driveDeltaLink.value) {
    await getDriveSnapshot();
  }
  if (!driveDeltaLink.value) return;

  intervalId = setInterval(async () => {
    const { hasAddedItems, hasNewFiles } = await checkFileUpdate();
    if (hasAddedItems) {
      isShowNotificationDot.value = true;
    }
    if (hasNewFiles) {
      clearInterval(intervalId);
    }
  }, 3000);
};

onMounted(startFileChangeDetection);
onBeforeUnmount(() => clearInterval(intervalId));

const switchAccount = async (account: LoginMethodMeta) => {
  const loading = ElLoading.service({
    lock: true,
    text: "Switching account...",
    background: "rgba(0, 0, 0, 0.7)",
    customClass: "custom-dynamsoft-message",
  });
  await loginManager.switchAccount(account);
  loading.close();
};

const logout = () => {
  loginManager.logout();
  fileListStore.$reset();
  loginManager.isLoggedIn = false;
  loginManager.isShowLoginPage = true;
};

watch(
  () => loginManager.loggedInAccounts,
  async () => {
    clearInterval(intervalId);
    await startFileChangeDetection();
  },
  { deep: true },
);

document.body.addEventListener("click", (e) => {
  if ((e.target as any).className === "user-info" || (e.target as any).closest(".user-info")) {
    return;
  }
  isShowAccountSelector.value = false;
});
</script>

<style scoped lang="scss">
.dashboard-header {
  width: 100%;
  height: 72px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;

  .left {
    width: 280px;
    border-right: 1px solid rgba(0, 0, 0, 0.2);
    padding-left: 18px;

    .dynamsoft-logo {
      width: 147px;
      height: 36px;
    }
  }

  .right {
    width: calc(100% - 280px);
    height: 100%;
    display: flex;
    align-items: center;
    padding-left: 16px;
    font-family: "Inter";
    font-weight: 600;
    vertical-align: middle;
    position: relative;

    .title {
      .title-part-1 {
        font-size: 16px;
        line-height: 20px;
        letter-spacing: 0%;
        color: rgba(0, 0, 0, 0.6);
      }

      .title-part-2 {
        font-size: 20px;
        line-height: 24px;
        letter-spacing: -1%;
      }
    }

    .refresh-and-account-bar {
      position: absolute;
      right: 15px;
      display: flex;
      align-items: center;

      .refresh-files {
        width: 140px;
        height: 40px;
        background-color: black;
        color: #fff;
        font-size: 14px;
        border-radius: 4px;
        display: flex;
        justify-content: center;
        align-items: center;
        margin-right: 50px;
        position: relative;
        cursor: pointer;

        .svg-icon {
          position: absolute;
          top: 5px;
          right: 5px;
        }

        &:hover {
          background-color: rgba(0, 0, 0, 0.8);
        }
      }
    }

    .to-login-page-btn {
      all: unset;
      width: 245px;
      height: 40px;
      box-sizing: border-box;
      background-color: #fe8e14;
      font-family: "Inter";
      font-weight: 600;
      border-radius: 4px;
      font-size: 14px;
      color: #ffffff;
      text-align: center;
      position: absolute;
      right: 30px;
      cursor: pointer;
    }

    .login-method-icon {
      width: 30px;
      height: 30px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-left: 15px;
      border-radius: 50%;
      background-color: #f2f2f2;
    }

    .user-info {
      width: 250px;
      height: 60px;
      display: flex;
      align-items: center;
      cursor: pointer;
      position: relative;

      &:hover {
        background-color: rgba(0, 0, 0, 0.05);
      }

      .account-info {
        font-family: "Inter";
        line-height: 20px;
        letter-spacing: 0%;
        vertical-align: middle;
        display: flex;
        flex-direction: column;
        margin-left: 15px;

        .service-name {
          font-size: 14px;
          line-height: 20px;
          font-weight: 600;
        }

        .user-email {
          font-size: 12px;
          line-height: 16px;
          font-weight: 400;
        }
      }
    }

    .storage-account-selector {
      width: 250px;
      position: absolute;
      top: 80px;
      right: 15px;
      background-color: #ffffff;
      border: 1px solid #e6e6e6;
      border-radius: 8px;
      box-shadow:
        0 0 0 1px rgba(0, 0, 0, 0.02),
        0 2px 6px rgba(0, 0, 0, 0.08);
      z-index: 99999;

      .logged-in-list {
        list-style: none;

        li {
          width: 100%;
          height: 60px;
          padding: 5px;
          border-bottom: 1px solid rgba(0, 0, 0, 0.2);
          cursor: pointer;

          .list-icon {
            width: 30px;
            height: 30px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 50%;
            background-color: #f2f2f2;
          }

          .account-item {
            height: 100%;
            display: flex;
            align-items: center;
            padding: 5px;

            &:hover {
              background-color: #f2f2f2;
            }
          }
        }

        .account-info {
          font-family: "Inter";
          font-weight: 600;
          font-size: 14px;
          vertical-align: middle;
          display: flex;
          flex-direction: column;
          align-items: flex-start;
          justify-content: center;
          padding: 0;
          margin-left: 10px;

          .user-email {
            font-size: 12px;
            line-height: 16px;
            font-weight: 400;
          }
        }

        .check-mark {
          position: absolute;
          right: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
        }
      }

      .connect-another-storage,
      .sign-out {
        width: 100%;
        height: 40px;
        padding-left: 15px;
        display: flex;
        align-items: center;
        font-family: "Inter";
        font-weight: 400;
        font-style: "Regular";
        font-size: 14px;
        line-height: 20px;
        letter-spacing: 0%;
        vertical-align: middle;
        cursor: pointer;

        .svg-icon {
          margin-right: 15px;
        }
      }

      .connect-another-storage {
        border-bottom: 1px solid rgba(0, 0, 0, 0.1);
      }
    }
  }
}
</style>
