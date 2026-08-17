<template>
  <div class="file-list-container" :style="{ width }">
    <div class="search">
      <div v-html="SEARCH" class="flex-center search-icon"></div>
      <input class="search-input" @input="searchFile" placeholder="Search" />
    </div>
    <div class="file-list" v-loading="fileListStore.listLoading" element-loading-text="Loading...">
      <NoFile
        v-if="!fileListStore.listLoading"
        :samplesVisible="hasSampleFiles"
        :showEmptyState="!hasAccountContent"
        :showNoFilesMessage="loginManager.isLoggedIn && !hasAccountContent"
        :showMobileAppButton="hasAccountContent"
        @showSampleFiles="showSampleFiles"
      >
        <template #file-list>
          <ListContent
            v-if="renderList.length || fileListStore.activeFolderPath"
            :navigationPhase="navigationPhase"
            :isNavigating="isNavigating"
            :setIsNavigating="setIsNavigating"
            :setNavigationPhase="setNavigationPhase"
            :updateFileList="props.updateFileList"
            :renderList="renderList"
            @removeSampleItem="removeSampleItem"
          />
        </template>
      </NoFile>
    </div>
    <div class="ew-resize" @pointerdown="startDrag" @pointermove="onPointerMove" @pointerup="stopDrag" @pointercancel="stopDrag" @lostpointercapture="stopDrag"></div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed, ref, watch } from "vue";
import { ANNOTATED_SUFFIX, ORIGINAL_SUFFIX, noExt, sortByTimestamp, stamp } from "../../../util/index.ts";
import { useFileListStore } from "../../../store/fileList.ts";
import { useImageTypeStore } from "../../../store/imageType.ts";
import loginManager from "../../../util/loginManager.ts";
import NoFile from "./NoFile.vue";
import ListContent from "./ListContent.vue";
import { SEARCH } from "../../../icons.ts";

const props = defineProps<{
  updateFileList: (folderPath?: string) => Promise<void>;
}>();

const fileListStore = useFileListStore();
const imageTypeStore = useImageTypeStore();
const currentFolderSegments = ref<string[]>([]);
const searchKeyWord = ref("");
const sampleFileModules = import.meta.glob("../../../assets/sampleFiles/*", {
  eager: true,
  query: "?url",
  import: "default",
}) as Record<string, string>;

const width = ref("280px");
const isDragging = ref(false);
const startX = ref(0);
const startWidth = ref(280);
const activePointerId = ref<number | null>(null);

const navigationPhase = ref<"idle" | "leaving">("idle");
const setNavigationPhase = (value: "idle" | "leaving") => {
  navigationPhase.value = value;
};
const isNavigating = ref(false);
const setIsNavigating = (value: boolean) => {
  isNavigating.value = value;
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

const renderList = computed(() => {
  const entries: any[] = [];
  const fileItemsByName = new Map<string, any>();

  sortByTimestamp([...fileListStore.list]).forEach((item: any) => {
    if (item.folder) {
      entries.push({ ...item, files: [item] });
      return;
    }

    const itemName = getItemName(item.name);
    const existingItem = fileItemsByName.get(itemName);
    if (existingItem) {
      existingItem.files.push(item);
      existingItem.isViewed = existingItem.files.every((file: any) => file.isViewed);
      return;
    }

    const fileItem = {
      ...item,
      id: `file-item-${itemName}`,
      name: itemName,
      files: [item],
      isSample: String(item.id).startsWith("sample:"),
    };
    fileItemsByName.set(itemName, fileItem);
    entries.push(fileItem);
  });

  const keyword = searchKeyWord.value.trim().toLowerCase();
  const filteredEntries = keyword ? entries.filter((entry: any) => entry.name.toLowerCase().includes(keyword) || entry.files.some((file: any) => file.name.toLowerCase().includes(keyword))) : entries;

  return filteredEntries;
});

const hasSampleFiles = computed(() => fileListStore.list.some((file: any) => String(file.id).startsWith("sample:")));
const hasAccountContent = computed(() => {
  if (!loginManager.isLoggedIn || hasSampleFiles.value) return false;
  return fileListStore.list.length > 0 || !!fileListStore.activeFolderPath;
});

const selectFirstFileGroup = () => {
  const firstFile = sortByTimestamp([...fileListStore.list]).find((file: any) => !file.folder);
  fileListStore.setSelectedFileName(firstFile?.name || "");
  if (!firstFile) return;

  const firstFileStamp = stamp(firstFile.name);
  const groupFiles = fileListStore.list.filter((file: any) => stamp(file.name) === firstFileStamp);
  const annotatedImage = groupFiles.find((file: any) => noExt(file.name).endsWith(ANNOTATED_SUFFIX));
  const originalImage = groupFiles.find((file: any) => noExt(file.name).endsWith(ORIGINAL_SUFFIX));
  if (annotatedImage || originalImage) {
    imageTypeStore.setImageType(annotatedImage ? ANNOTATED_SUFFIX : ORIGINAL_SUFFIX);
  }
  groupFiles.forEach((file: any) => fileListStore.markViewed(file));
};

const showSampleFiles = () => {
  const sampleFiles = Object.entries(sampleFileModules).map(([path, url]) => {
    const name = path.split("/").pop()!;
    const extension = name.split(".").pop()?.toLowerCase();
    return {
      id: `sample:${url}`,
      name,
      image: ["bmp", "jpeg", "jpg", "png"].includes(extension || ""),
    };
  });

  fileListStore.setActiveFolderPath("");
  fileListStore.setFileList(sortByTimestamp(sampleFiles));
  selectFirstFileGroup();
};

const initializeFileList = async () => {
  await props.updateFileList();
  if (!fileListStore.list.length && !fileListStore.activeFolderPath) {
    showSampleFiles();
  }
};

const removeSampleItem = (fileItem: any) => {
  const removedIds = new Set(fileItem.files.map((file: any) => file.id));
  const removedSelectedItem = getItemName(fileListStore.selectedFileName) === fileItem.name;
  fileListStore.setFileList(fileListStore.list.filter((file: any) => !removedIds.has(file.id)));
  if (removedSelectedItem) {
    selectFirstFileGroup();
  }
};

const clampWidth = (value: number) => Math.min(560, Math.max(220, value));

const startDrag = (event: PointerEvent) => {
  const handle = event.currentTarget as HTMLElement | null;
  if (!handle || !renderList.value.length) return;
  isDragging.value = true;
  startX.value = event.clientX;
  startWidth.value = parseInt(width.value, 10);
  activePointerId.value = event.pointerId;
  handle.setPointerCapture(event.pointerId);
};

const onPointerMove = (event: PointerEvent) => {
  if (!isDragging.value || event.pointerId !== activePointerId.value) return;
  const deltaX = event.clientX - startX.value;
  width.value = `${clampWidth(startWidth.value + deltaX)}px`;
};

const stopDrag = (event: PointerEvent) => {
  if (activePointerId.value !== null && event.pointerId !== activePointerId.value) return;
  const handle = event.currentTarget as HTMLElement | null;
  if (handle && event.pointerId !== undefined && handle.hasPointerCapture(event.pointerId)) {
    handle.releasePointerCapture(event.pointerId);
  }
  isDragging.value = false;
  activePointerId.value = null;
};

const searchFile = (e: Event) => {
  const target = e.target as HTMLInputElement | null;
  searchKeyWord.value = target?.value || "";
};

watch(
  () => fileListStore.refreshRequest,
  () => {
    currentFolderSegments.value = fileListStore.activeFolderPath ? fileListStore.activeFolderPath.split("/") : [];
    navigationPhase.value = "idle";
    isNavigating.value = false;
  },
);

watch(
  () => loginManager.loggedInAccounts,
  async () => {
    currentFolderSegments.value = [];
    navigationPhase.value = "idle";
    isNavigating.value = false;
    fileListStore.setActiveFolderPath("");
    await initializeFileList();
  },
  { deep: true },
);

onMounted(initializeFileList);
</script>

<style scoped lang="scss">
.file-list-container {
  height: 100%;
  border-right: 1px solid rgba(0, 0, 0, 0.2);
  padding: 15px;
  position: relative;

  .search {
    width: 100%;
    height: 32px;
    opacity: 1;
    border-radius: 4px;
    padding: 0 10px;
    border: 1px solid var(--Color-Border-border-color-darker, rgba(205, 208, 214, 1));
    margin-bottom: 10px;
    display: flex;
    align-items: center;

    .search-icon {
      margin-right: 8px;
    }

    .search-input {
      width: 100%;
      height: 100%;
      font-family: "Inter";
      font-weight: 400;
      font-size: 14px;
      line-height: 20px;
      letter-spacing: 0%;
      vertical-align: middle;
      border: none;

      &:focus {
        outline: none;
        box-shadow: none;
      }

      &::placeholder {
        color: rgba(168, 171, 178, 1);
        font-size: 14px;
      }
    }
  }

  .file-list {
    height: calc(100% - 42px);
    overflow: hidden;
    position: relative;

    :deep(.dynamsoft-loading-spinner) {
      top: 8%;
    }
  }

  .ew-resize {
    width: 5px;
    height: 100%;
    position: absolute;
    top: 0;
    right: 0;
    touch-action: none;

    &:hover {
      cursor: ew-resize;
    }
  }
}
</style>

<style lang="scss">
.file-list-tooltip,
.file-list-tooltip * {
  transition: none !important;
  animation: none !important;
}
</style>
