<template>
  <div class="dashboard">
    <LoginMethodPanel :isShowMask="true" :showContinueAsGuest="false" v-show="isShowLoginMethodPanel" :updateLoginMethodPanelVisibility="updateLoginMethodPanelVisibility" />
    <Header :updateLoginMethodPanelVisibility="updateLoginMethodPanelVisibility" />
    <div class="dashboard-content">
      <FileList :updateFileList="updateFileList" />
      <div class="detail-panel">
        <Toolbar :updateFileList="updateFileList" />
        <div ref="resizableAreaRef" class="resizable-area">
          <div class="image-preview-panel" :style="{ height: `${previewRatio}%` }">
            <ImagePreview :updateFileList="updateFileList" />
          </div>
          <div class="ns-resize" @pointerdown="startVerticalDrag" @pointermove="onVerticalDrag" @pointerup="stopVerticalDrag" @pointercancel="stopVerticalDrag" @lostpointercapture="stopVerticalDrag"></div>
          <div class="results-table-panel" :style="{ height: `${100 - previewRatio}%` }">
            <ResultsTablePreview :updateFileList="updateFileList" />
          </div>
        </div>
      </div>
    </div>
  </div>
  <el-dialog v-model="dialogStore.continueMethodDialog" top="30vh" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false" header-class="dialog-header" title="How would you like to continue?" width="500">
    <div class="dialog-desc">
      <span>To access your own files, authorize your cloud storage. You can also continue as a guest to explore the sample interface.</span>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" class="continue-as-guest" @click="continueAsGuest">Continue as Guest</el-button>
        <el-button type="primary" class="authorize" @click="toAuthorize">Authorize</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import FileList from "./FileList/FileList.vue";
import Header from "./Header.vue";
import ImagePreview from "./Preview/ImagePreview.vue";
import ResultsTablePreview from "./Preview/TablePreview.vue";
import LoginMethodPanel from "../LoginMethodPanel.vue";
import storageResourceService from "../../util/storageResourceService";
import loginManager from "../../util/loginManager.ts";
import Toolbar from "./Toolbar.vue";
import { ref, useTemplateRef } from "vue";
import { useFileListStore } from "../../store/fileList";
import { ANNOTATED_SUFFIX, noExt, ORIGINAL_SUFFIX, sortByTimestamp, stamp } from "../../util";
import { useImageTypeStore } from "../../store/imageType";
import { useDialogStore } from "../../store/dialog.ts";

const fileListStore = useFileListStore();
const dialogStore = useDialogStore();
const imageTypeStore = useImageTypeStore();
const isShowLoginMethodPanel = ref(false);
const previewRatio = ref(50);
const startPreviewRatio = ref(50);
const isDraggingVertical = ref(false);
const startY = ref(0);
const activePointerId = ref<number | null>(null);
const resizableAreaRef = useTemplateRef("resizableAreaRef");

const clampRatio = (value: number) => Math.min(80, Math.max(20, value));

const startVerticalDrag = (event: PointerEvent) => {
  const handle = event.currentTarget as HTMLElement | null;
  if (!handle || !resizableAreaRef.value) return;
  isDraggingVertical.value = true;
  startY.value = event.clientY;
  startPreviewRatio.value = previewRatio.value;
  activePointerId.value = event.pointerId;
  handle.setPointerCapture(event.pointerId);
};

const onVerticalDrag = (event: PointerEvent) => {
  if (!isDraggingVertical.value || event.pointerId !== activePointerId.value || !resizableAreaRef.value) return;
  const areaHeight = resizableAreaRef.value.clientHeight;
  if (!areaHeight) return;
  const deltaY = event.clientY - startY.value;
  const deltaRatio = (deltaY / areaHeight) * 100;
  previewRatio.value = clampRatio(startPreviewRatio.value + deltaRatio);
};

const stopVerticalDrag = (event: PointerEvent) => {
  if (activePointerId.value !== null && event.pointerId !== activePointerId.value) return;
  const handle = event.currentTarget as HTMLElement | null;
  if (handle && handle.hasPointerCapture(event.pointerId)) {
    handle.releasePointerCapture(event.pointerId);
  }
  isDraggingVertical.value = false;
  activePointerId.value = null;
};

const updateFileList = async (folderName?: string) => {
  try {
    fileListStore.setListLoading(true);
    const { items: files } = await storageResourceService.getFileList({ folderName });
    console.log("files: ", files);
    fileListStore.setFileList(sortByTimestamp(files));
    const selectedFile = fileListStore.list.find((file: any) => !file.folder);
    const selectedStamp = selectedFile ? stamp(selectedFile.name) : undefined;
    const annotatedImage = selectedFile && fileListStore.list.find((file: any) => file.image && stamp(file.name) === selectedStamp && noExt(file.name).endsWith(ANNOTATED_SUFFIX));
    const originalImage = selectedFile && fileListStore.list.find((file: any) => file.image && stamp(file.name) === selectedStamp && noExt(file.name).endsWith(ORIGINAL_SUFFIX));
    const selectedImage = annotatedImage || originalImage;

    if (selectedImage) {
      imageTypeStore.setImageType(annotatedImage ? ANNOTATED_SUFFIX : ORIGINAL_SUFFIX);
    }
    fileListStore.setSelectedFileName(selectedFile?.name || "");
    if (selectedFile) {
      fileListStore.markViewed(selectedFile);
      if (selectedImage) fileListStore.markViewed(selectedImage);
    }
  } finally {
    fileListStore.setListLoading(false);
  }
};

const updateLoginMethodPanelVisibility = (visible: boolean) => {
  isShowLoginMethodPanel.value = visible;
};

const continueAsGuest = () => {
  dialogStore.setDialogVisible({ continueMethodDialog: false });
};

const toAuthorize = () => {
  loginManager.isShowLoginPage = true;
  localStorage.setItem("BBS_Web_Viewer_First_Screen", "Login");
};
</script>

<style scoped lang="scss">
.dashboard {
  width: 100%;
  height: 100%;

  .dashboard-content {
    width: 100%;
    height: calc(100% - 72px);
    display: flex;

    .detail-panel {
      flex: 1;
      min-width: 0;
      height: 100%;
      padding: 0 16px 8px;
      display: flex;
      flex-direction: column;

      .resizable-area {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
      }

      .image-preview-panel,
      .results-table-panel {
        width: 100%;
        min-height: 0;
      }

      .ns-resize {
        width: 100%;
        height: 10px;
        cursor: ns-resize;
        touch-action: none;
      }
    }
  }
}

.dialog-header {
  font-family: "Inter";
  font-weight: 400;
  font-size: 18px;
}

.dialog-desc {
  font-family: "Inter";
  font-weight: 400;
  font-size: 16px;
}

.continue-as-guest {
  border: 1px solid #dcdfe6;
  background-color: #ffffff;
  color: #606266;
  font-family: "Inter";
  font-weight: 500;
  font-size: 14px;
}

.authorize {
  font-family: "Inter";
  font-weight: 500;
  font-size: 14px;
  background-color: #fe8e14;
  color: #ffffff;
  border: none;
}
</style>
