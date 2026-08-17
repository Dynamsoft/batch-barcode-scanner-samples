<template>
  <div class="tool-bar">
    <div class="current-file-bar">
      <div class="file-name">{{ fileName }}</div>
      <div class="download" v-html="DOWNLOAD" @click="downloadFile"></div>
      <div class="delete" v-html="DELETE" @click="confirmDelete"></div>
    </div>
    <div class="view-controls">
      <div class="controller-container" @click="setZoom('plus')" v-html="MAGNIFYING_GLASS_PLUS"></div>
      <div class="controller-container" @click="setZoom('minus')" v-html="MAGNIFYING_GLASS_MINUS"></div>
      <div class="controller-container" @click="rotateAngleStore.setAngle(rotateAngleStore.angle - 90)" v-html="ROTATE_COUNTERCLOCKWISE"></div>
      <div class="controller-container" @click="rotateAngleStore.setAngle(rotateAngleStore.angle + 90)" v-html="ROTATE_CLOCKWISE"></div>
      <div class="controller-container" @click="toggleMaxZoom" v-html="TOGGLE_MAX_ZOOM"></div>
    </div>
    <div class="image-type-options">
      <el-switch v-model="hideMarks" :disabled="isDisableRadio" style="--dynamsoft-switch-on-color: #fe8e14" />
      <span>Hide Marks</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useFileListStore } from "../../store/fileList";
import { useMagnifyingGlassStore } from "../../store/magnifyingGlass";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRotateAngleStore } from "../../store/rotateAngle";
import { useImageTypeStore } from "../../store/imageType";
import { ANNOTATED_SUFFIX, noExt, ORIGINAL_SUFFIX, stamp } from "../../util";
import { DELETE, DOWNLOAD, MAGNIFYING_GLASS_PLUS, ROTATE_CLOCKWISE, ROTATE_COUNTERCLOCKWISE, MAGNIFYING_GLASS_MINUS, TOGGLE_MAX_ZOOM } from "../../icons";
import type { OriginalOrAnnotated } from "../../util/types";
import { watch, computed } from "vue";
import JSZip from "jszip";
import storageResourceService from "../../util/storageResourceService";

const props = defineProps<{
  updateFileList: (folderPath?: string) => Promise<void>;
}>();

const fileListStore = useFileListStore();
const magnifyingGlassStore = useMagnifyingGlassStore();
const rotateAngleStore = useRotateAngleStore();
const imageTypeStore = useImageTypeStore();
const isDisableRadio = computed(() => {
  const hasImage = fileListStore.list.find((file: any) => {
    return stamp(file.name) === fileListStore.selectedFileName && file.image;
  });
  return !!hasImage;
});
const hideMarks = computed({
  get: () => imageTypeStore.imageType === ORIGINAL_SUFFIX,
  set: (value: boolean) => updateImageType(value ? ORIGINAL_SUFFIX : ANNOTATED_SUFFIX),
});

const fileName = computed(() => {
  const selectedFileName = noExt(fileListStore.selectedFileName);
  return selectedFileName ? selectedFileName : "No files";
});

watch(
  () => imageTypeStore.imageType,
  () => {
    fileListStore.triggerRefresh(false);
  },
);

const updateImageType = (type: OriginalOrAnnotated) => {
  imageTypeStore.setImageType(type === ANNOTATED_SUFFIX ? ANNOTATED_SUFFIX : ORIGINAL_SUFFIX);
  const currentFileName = fileListStore.selectedFileName;
  if (type === ANNOTATED_SUFFIX) {
    fileListStore.setSelectedFileName(currentFileName.replace(ORIGINAL_SUFFIX, ANNOTATED_SUFFIX));
  } else {
    fileListStore.setSelectedFileName(currentFileName.replace(ANNOTATED_SUFFIX, ORIGINAL_SUFFIX));
  }
  const targetItem = fileListStore.list.find((fileItem) => {
    return stamp(fileItem.name) === stamp(fileListStore.selectedFileName) && fileItem.name.includes(imageTypeStore.imageType);
  });
  if (targetItem) {
    fileListStore.markViewed(targetItem);
  }
};

const triggerBlobDownload = (blob: Blob, fileName: string) => {
  const downloadUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = downloadUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(downloadUrl);
};

const downloadFile = async () => {
  const selectedFileName = fileListStore.selectedFileName;
  if (!selectedFileName) return;
  const groupStamp = stamp(selectedFileName);
  const groupItems = fileListStore.list.filter((fileItem: any) => {
    if (fileItem.folder) return false;
    // Files without a timestamp can't be grouped, fall back to the selected file only.
    return groupStamp ? stamp(fileItem.name) === groupStamp : fileItem.name === selectedFileName;
  });
  const downloadtStartElMessage = ElMessage.primary({
    message: "Preparing file for download...",
    duration: 0,
    customClass: "custom-dynamsoft-message",
  });
  try {
    const zip = new JSZip();
    const results = await Promise.all(
      groupItems.map(async (fileItem: any) => {
        try {
          return { name: fileItem.name, blob: await storageResourceService.getFileBinary(fileItem.id) };
        } catch {
          return null;
        }
      }),
    );

    results.forEach((result) => {
      if (result?.blob) {
        zip.file(result.name, result.blob);
      }
    });

    if (Object.keys(zip.files).length === 0) {
      ElMessage({
        message: "No file available for download!",
        type: "warning",
        showClose: true,
        duration: 2000,
        customClass: "custom-dynamsoft-message",
      });
      return;
    }

    const baseFileName = noExt(selectedFileName).replace(ORIGINAL_SUFFIX, "").replace(ANNOTATED_SUFFIX, "") || "download";
    const zipBlob = await zip.generateAsync({ type: "blob" });
    triggerBlobDownload(zipBlob, `${baseFileName}.zip`);
  } finally {
    downloadtStartElMessage.close();
  }
};

const setZoom = (type: "plus" | "minus") => {
  if (!fileListStore.selectedFileName) return;
  let zoomLevel = 1;
  if (type === "plus") {
    if (magnifyingGlassStore.zoom >= 1) {
      zoomLevel = magnifyingGlassStore.zoom + 0.5;
    } else {
      zoomLevel = magnifyingGlassStore.zoom + 0.1;
    }
  } else if (type === "minus") {
    if (magnifyingGlassStore.zoom > 1) {
      zoomLevel = magnifyingGlassStore.zoom - 0.5;
    } else {
      zoomLevel = magnifyingGlassStore.zoom - 0.1;
    }
  }
  magnifyingGlassStore.setZoom(zoomLevel);
};

const toggleMaxZoom = () => {
  if (!fileListStore.selectedFileName) return;
  magnifyingGlassStore.setZoom(magnifyingGlassStore.zoom === 5 ? 1 : 5);
};

const confirmDelete = async () => {
  const targetItems = fileListStore.list.filter((fileItem) => {
    return stamp(fileItem.name) === stamp(fileListStore.selectedFileName);
  });
  if (targetItems.some((fileItem) => String(fileItem.id).startsWith("sample:"))) {
    ElMessage({ message: "Sample files cannot be deleted. Use the close button in the file list to hide them.", type: "warning", duration: 2000, customClass: "custom-dynamsoft-message" });
    return;
  }
  if (targetItems.length === 0) {
    ElMessage({ message: "No file found to delete!", type: "warning", duration: 2000, customClass: "custom-dynamsoft-message" });
    return;
  }
  try {
    await ElMessageBox.confirm("Are you sure you want to delete all files in this group? This action cannot be undone.", "Delete Files", {
      confirmButtonText: "Delete",
      cancelButtonText: "Cancel",
      type: "warning",
      customClass: "custom-dynamsoft-message-box",
    });
    await deleteFile(targetItems);
  } catch {}
};

const deleteFile = async (targetItems: any[]) => {
  try {
    const message = ElMessage({ message: "Deleting file...", type: "primary", duration: 2000, customClass: "custom-dynamsoft-message" });
    await storageResourceService.deleteFile(targetItems);
    ElMessage({ message: "File deleted successfully.", type: "success", duration: 2000, customClass: "custom-dynamsoft-message" });
    message.close();
    await props.updateFileList(fileListStore.activeFolderPath);
    fileListStore.triggerRefresh();
  } catch (ex: any) {
    ElMessage({ message: "Failed to delete file.", type: "error", duration: 2000, customClass: "custom-dynamsoft-message" });
  }
};
</script>

<style scoped lang="scss">
.tool-bar {
  width: 100%;
  height: 50px;
  display: flex;
  align-items: center;
  position: relative;

  .current-file-bar {
    width: fit-content;
    display: flex;
    align-items: center;
    border-right: 1px solid rgba(0, 0, 0, 0.2);
    margin-right: 30px;

    .file-name {
      font-family: "Inter";
      font-weight: 400;
      font-size: 14px;
      margin-right: 8px;
      color: rgba(96, 98, 102, 1);
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .download,
    .delete {
      width: 25px;
      height: 25px;
      display: flex;
      margin-right: 10px;
      justify-content: center;
      align-items: center;
      cursor: pointer;

      &:hover {
        background-color: rgba(245, 245, 245, 1);
      }
    }
  }

  .view-controls {
    width: 200px;
    flex: 0 0 200px;
    display: flex;
    align-items: center;
    justify-content: space-between;

    .controller-container {
      width: 28px;
      height: 28px;
      display: flex;
      justify-content: center;
      align-items: center;
      cursor: pointer;

      &:hover {
        background-color: rgba(245, 245, 245, 1);
      }
    }
  }

  .image-type-options {
    margin-left: auto;
    font-family: "Inter";
    font-weight: 400;
    font-size: 14px;
    margin-right: 5px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: rgba(48, 49, 51, 1);
  }
}
</style>
