<template>
  <div class="to-previous-level" v-if="showPreviousLevel" @click="goToPreviousLevel">
    <div v-html="LEFT_ARROW" class="flex-center"></div>
    <span>{{ previousLevelName }}</span>
  </div>
  <div v-if="renderList.length" class="list-scrollbar">
    <div ref="fileListContentRef" class="file-list-content" :class="{ 'is-leaving-left': props.navigationPhase === 'leaving' }">
      <div v-for="(fileItem, itemIndex) in renderList" :key="`item-${itemIndex}-${fileItem.id ?? itemIndex}`" class="file-item-wrapper">
        <div class="file-item-content">
          <el-tooltip :content="fileItem.name" placement="right" popper-class="file-list-tooltip" :hide-after="0" :disabled="tooltipDisabledMap[fileItem.id] ?? true">
            <div class="list-file-item" @mouseenter="updateTooltipState($event, fileItem.id)" @click="changeSelectedFile(fileItem)">
              <div class="file-svg" v-html="getSvg(fileItem)"></div>
              <div class="file-name" :style="{ color: getRowTextColor(fileItem), fontWeight: fileItem.isViewed ? '400' : '600' }">{{ fileItem.name }}</div>
              <button v-if="fileItem.isSample" class="remove-sample" type="button" aria-label="Hide sample file" @click.stop="emit('removeSampleItem', fileItem)">
                <div v-html="REMOVE" class="flex-center"></div>
              </button>
              <div v-html="RIGHT_ARROW" class="flex-center" v-show="fileItem.folder"></div>
            </div>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef } from "vue";
import { ANNOTATED_SUFFIX, noExt, ORIGINAL_SUFFIX } from "../../../util";
import { useFileListStore } from "../../../store/fileList.ts";
import { useImageTypeStore } from "../../../store/imageType.ts";
import { FILE_CHECKED, FILE_READ, FILE_UNREAD, FOLDER_READ, FOLDER_UNREAD, LEFT_ARROW, REMOVE, RIGHT_ARROW } from "../../../icons.ts";

const props = defineProps<{
  navigationPhase: "idle" | "leaving";
  setNavigationPhase: (value: "idle" | "leaving") => void;
  isNavigating: boolean;
  setIsNavigating: (value: boolean) => void;
  updateFileList: (folderPath?: string) => Promise<void>;
  renderList: any[];
}>();

const emit = defineEmits<{
  removeSampleItem: [fileItem: any];
}>();

const tooltipDisabledMap = ref<Record<string, boolean>>({});
const currentFolderSegments = computed(() => (fileListStore.activeFolderPath ? fileListStore.activeFolderPath.split("/") : []));
const showPreviousLevel = computed(() => currentFolderSegments.value.length > 0);
const fileListContentRef = useTemplateRef("fileListContentRef");
const fileListStore = useFileListStore();
const imageTypeStore = useImageTypeStore();

const previousLevelName = computed(() => {
  const segments = currentFolderSegments.value;
  if (segments.length === 0) return "";
  if (segments.length === 1) return segments[0];
  return segments[segments.length - 1];
});

const goToPreviousLevel = async () => {
  if (!showPreviousLevel.value) return;
  await navigateToFolder(currentFolderSegments.value.slice(0, -1));
};

const navigateToFolder = async (nextSegments: string[]) => {
  if (props.isNavigating) return;
  props.setIsNavigating(true);
  try {
    props.setNavigationPhase("leaving");
    await nextTick();

    if (fileListContentRef.value) {
      await waitForTransition(fileListContentRef.value);
    }

    const oldPath = fileListStore.activeFolderPath;
    fileListStore.setListLoading(true);
    const targetFolderPath = nextSegments.join("/");
    fileListStore.setActiveFolderPath(targetFolderPath);
    await props.updateFileList(targetFolderPath);

    const targetFolderObject = fileListStore.list.find((item) => item.name === oldPath);
    if (targetFolderObject) {
      fileListStore.markViewed(targetFolderObject);
    }

    props.setNavigationPhase("idle");
  } finally {
    props.setIsNavigating(false);
  }
};

const getItemName = (fileName: string) => {
  const baseName = noExt(fileName);
  if (baseName.endsWith(ANNOTATED_SUFFIX)) {
    return baseName.slice(0, -ANNOTATED_SUFFIX.length);
  }
  if (baseName.endsWith(ORIGINAL_SUFFIX)) {
    return baseName.slice(0, -ORIGINAL_SUFFIX.length);
  }
  return baseName;
};

const waitForTransition = (element: HTMLElement) => {
  return new Promise<void>((resolve) => {
    const handler = () => {
      element.removeEventListener("transitionend", handler);
      resolve();
    };
    element.addEventListener("transitionend", handler, { once: true });
  });
};

const getSvg = (fileItem: any) => {
  if (fileItem.folder) {
    return fileItem.isViewed ? FOLDER_READ : FOLDER_UNREAD;
  }

  const isChecked = getItemName(fileListStore.selectedFileName) === fileItem.name;

  if (isChecked) {
    return FILE_CHECKED;
  }

  if (fileItem.isViewed) {
    return FILE_READ;
  }

  return FILE_UNREAD;
};

const updateTooltipState = (event: MouseEvent, rowId: string) => {
  const rowContent = event.currentTarget as HTMLElement | null;
  const fileNameElement = rowContent?.querySelector(".file-name") as HTMLElement | null;
  if (!fileNameElement) return;
  tooltipDisabledMap.value[rowId] = fileNameElement.scrollWidth <= fileNameElement.clientWidth;
};

const getRowTextColor = (fileItem: any) => {
  const isSelected = !fileItem.folder && getItemName(fileListStore.selectedFileName) === fileItem.name;
  if (isSelected) {
    return "rgba(254, 142, 20, 1)";
  }
  if (fileItem.isViewed) {
    return "#606266";
  }
  return "#000000";
};

const changeSelectedFile = async (fileItem: any) => {
  if (fileItem.folder) {
    fileListStore.markViewed(fileItem);
    await navigateToFolder([...currentFolderSegments.value, fileItem.name]);
    return;
  }

  const csvFile = fileItem.files.find((file: any) => file.name.endsWith(".csv"));
  const annotatedImage = fileItem.files.find((file: any) => noExt(file.name).endsWith(ANNOTATED_SUFFIX));
  const originalImage = fileItem.files.find((file: any) => noExt(file.name).endsWith(ORIGINAL_SUFFIX));
  const selectedImage = annotatedImage || originalImage;

  if (selectedImage) {
    imageTypeStore.setImageType(annotatedImage ? ANNOTATED_SUFFIX : ORIGINAL_SUFFIX);
  }
  fileListStore.setSelectedFileName(csvFile?.name || selectedImage?.name || fileItem.files[0].name);
  fileItem.files.forEach((file: any) => fileListStore.markViewed(file));
};
</script>

<style scoped lang="scss">
.to-previous-level {
  width: 100%;
  height: 50px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  cursor: pointer;

  span {
    font-family: "Inter";
    font-weight: 400;
    font-style: "Regular";
    font-size: 14px;
    line-height: 20px;
    letter-spacing: 0%;
    vertical-align: middle;
    margin-left: 8px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.file-list-content {
  height: 100%;
  transition:
    transform 0.22s ease,
    opacity 0.22s ease;
  will-change: transform, opacity;
}

.file-list-content.is-leaving-left {
  transform: translateX(-100%);
  opacity: 0;
}

.file-item-wrapper {
  width: 100%;
}

.file-item-content {
  padding-left: 5px;
}

.list-file-item {
  height: 50px;
  font-family: "Inter";
  font-weight: 400;
  font-size: 14px;
  padding: 0 8px;
  border: none;
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  cursor: pointer;

  &:hover {
    background-color: #f5f5f5;
  }

  .file-svg {
    display: flex;
    justify-content: center;
    align-items: center;
    margin-right: 5px;
    flex-shrink: 0;
  }

  .file-name {
    flex: 1;
    min-width: 0;
    margin-right: 5px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .remove-sample {
    width: 24px;
    height: 24px;
    padding: 4px;
    border: 0;
    background: transparent;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    cursor: pointer;
  }
}
</style>
