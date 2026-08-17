<script setup lang="ts">
import Dashboard from "./components/BatchScannerWebViewer/Dashboard.vue";
import Login from "./components/Login.vue";
import loginManager from "./util/loginManager";
import { useDialogStore } from "./store/dialog.ts";
import { useFileListStore } from "./store/fileList";
import { WARNING } from "./icons.ts";

const fileListStore = useFileListStore();
const reAuthorizeDialogStore = useDialogStore();

const reAuthorize = () => {
  loginManager.logout();
  fileListStore.$reset();
  reAuthorizeDialogStore.setDialogVisible({ reAuthDialog: false });
};
function handleClose() {
  reAuthorizeDialogStore.setDialogVisible({ reAuthDialog: false });
}
</script>

<template>
  <el-config-provider namespace="dynamsoft">
    <main class="batch-barcode-scanner-web-viewer">
      <Login v-if="loginManager.isShowLoginPage" />
      <Dashboard v-else />
      <el-dialog v-model="reAuthorizeDialogStore.reAuthDialog" top="30vh" :close-on-click-modal="false" :close-on-press-escape="false" header-class="dialog-header" title="Session Expired" width="500" :before-close="handleClose">
        <div class="dialog-desc">
          <div v-html="WARNING" class="icon-svg flex-center"></div>
          <span>Your session has expired. Please re-authorize to continue.</span>
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button type="primary" class="dialog-footer-reauthorize" @click="reAuthorize">Re-authorize</el-button>
          </div>
        </template>
      </el-dialog>
    </main>
  </el-config-provider>
</template>

<style scoped lang="scss">
.batch-barcode-scanner-web-viewer {
  width: 100%;
  height: 100%;

  .dialog-footer {
    display: flex;
    justify-content: center;
    align-items: center;

    .dialog-footer-reauthorize {
      font-family: "Inter";
      font-weight: 500;
      font-size: 14px;
      border: none;
      background-color: #fe8e14;
    }
  }

  :deep(.dialog-header) {
    font-family: "Inter";
    font-weight: 400;
    font-size: 18px;
    line-height: 26px;
    letter-spacing: 0px;
  }

  .dialog-desc {
    display: flex;
    align-items: center;
    font-family: "Inter";
    font-weight: 400;
    font-style: "Regular";
    font-size: 16px;
    line-height: 24px;
    letter-spacing: 0px;

    .icon-svg {
      margin-right: 8px;
    }
  }
}
</style>
