<template>
  <div class="image-preview">
    <div class="image-preview-content" ref="imagePreviewContentRef">
      <img
        :src="imageUrl"
        :style="{ transform: `translate(${offsetX}px, ${offsetY}px) scale(${magnifyingGlassStore.zoom}) rotate(${rotateAngleStore.angle}deg)` }"
        v-show="imageUrl"
        draggable="false"
        @dragstart.prevent
        @wheel.prevent="onWheel"
        @mousedown="startDrag"
        @mousemove="onMouseMove"
        @mouseup="stopDrag"
        @mouseleave="stopDrag"
        alt="image"
      />
      <div class="images-missing" v-show="isShowImageMissing">
        <div v-html="IMAGE_MISSING" class="flex-center"></div>
        <span class="title">Images Missing</span>
        <span class="desc">This package is missing image file. Please refresh and try again.</span>
        <button class="refresh-btn" @click="refresh">Refresh</button>
      </div>
      <div class="no-image" v-show="!imageUrl && !isShowImageMissing">
        <div v-html="NO_IMAGE" class="svg-icon flex-center"></div>
        <p>No images uploaded from the Batch Barcode Scanner app yet.</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, useTemplateRef, watch, nextTick, onUnmounted } from "vue";
import { useFileListStore } from "../../../store/fileList";
import { ElLoading } from "element-plus";
import { useMagnifyingGlassStore } from "../../../store/magnifyingGlass";
import { useRotateAngleStore } from "../../../store/rotateAngle";
import storageResourceService from "../../../util/storageResourceService";
import { ANNOTATED_SUFFIX, noExt, ORIGINAL_SUFFIX, stamp } from "../../../util";
import { useImageTypeStore } from "../../../store/imageType";
import { IMAGE_MISSING, NO_IMAGE } from "../../../icons";

const props = defineProps<{
  updateFileList: (folderPath?: string) => Promise<void>;
}>();

const fileListStore = useFileListStore();
const magnifyingGlassStore = useMagnifyingGlassStore();
const rotateAngleStore = useRotateAngleStore();
const imageTypeStore = useImageTypeStore();

const isShowImageMissing = ref(false);
const imageUrl = ref("");
const imagePreviewContentRef = useTemplateRef("imagePreviewContentRef");
let fetchId = 0;
let loadingServiceMap: Map<number, ReturnType<typeof ElLoading.service>> = new Map();
let controller: AbortController | null = null;
const imageUrlCache = new Map<string, string>();

const removeCachedImage = (imageFileId: string) => {
  const cachedImageUrl = imageUrlCache.get(imageFileId);
  if (!cachedImageUrl) return;
  URL.revokeObjectURL(cachedImageUrl);
  imageUrlCache.delete(imageFileId);
};

const isDragging = ref(false);
const offsetX = ref(0);
const offsetY = ref(0);
const startX = ref(0);
const startY = ref(0);

const startDrag = (event: MouseEvent) => {
  isDragging.value = true;
  startX.value = event.clientX - offsetX.value;
  startY.value = event.clientY - offsetY.value;
};

const onMouseMove = (event: MouseEvent) => {
  if (!isDragging.value) return;
  offsetX.value = event.clientX - startX.value;
  offsetY.value = event.clientY - startY.value;
};

const stopDrag = () => {
  isDragging.value = false;
};

const onWheel = (event: WheelEvent) => {
  const delta = event.deltaY > 0 ? -0.1 : 0.1;
  magnifyingGlassStore.setZoom(Math.min(Math.max(magnifyingGlassStore.zoom + delta, 0.1), 5));
};

const fetchImageFile = async (newFileName: string, forceRefresh: boolean) => {
  const currentFetchId = ++fetchId;

  const oldFetchId = currentFetchId - 1;
  if (loadingServiceMap.has(oldFetchId)) {
    loadingServiceMap.get(oldFetchId)?.close();
    loadingServiceMap.delete(oldFetchId);
  }

  if (controller) {
    controller.abort();
    controller = null;
  }

  if (!newFileName) {
    isShowImageMissing.value = false;
    imageUrl.value = "";
    return;
  }

  try {
    await nextTick();
    const imageFile = fileListStore.selectedImageFile;
    if (!imageFile?.id || !imageTypeStore.hasCurrentType) {
      throw new Error("Image not found");
    }
    const imageFileId = String(imageFile.id);

    if (forceRefresh) {
      removeCachedImage(imageFileId);
    } else {
      const cachedImageUrl = imageUrlCache.get(imageFileId);
      if (cachedImageUrl) {
        isShowImageMissing.value = false;
        imageUrl.value = cachedImageUrl;
        return;
      }
    }

    isShowImageMissing.value = false;
    loadingServiceMap.set(
      currentFetchId,
      ElLoading.service({
        lock: true,
        text: "Loading",
        background: "rgba(0, 0, 0, 0.7)",
        target: imagePreviewContentRef.value!,
      }),
    );

    controller = new AbortController();
    const image = await storageResourceService.getFileBinary(imageFileId, forceRefresh, controller);

    if (currentFetchId !== fetchId) return;
    if (!loadingServiceMap.has(currentFetchId)) return;

    const nextImageUrl = URL.createObjectURL(image!);
    imageUrlCache.set(imageFileId, nextImageUrl);
    imageUrl.value = nextImageUrl;
  } catch (ex: any) {
    if (ex.name === "AbortError") return;
    isShowImageMissing.value = true;
    imageUrl.value = "";
  } finally {
    loadingServiceMap.get(currentFetchId)?.close();
    loadingServiceMap.delete(currentFetchId);
    if (currentFetchId === fetchId) {
      controller = null;
    }
  }
};

const refresh = async () => {
  isShowImageMissing.value = false;
  const currentFetchId = ++fetchId;
  const folderPath = fileListStore.activeFolderPath;
  try {
    loadingServiceMap.set(
      currentFetchId,
      ElLoading.service({
        lock: true,
        text: "Loading...",
        background: "rgba(0, 0, 0, 0.7)",
        target: imagePreviewContentRef.value!,
      }),
    );
    let image;
    const imageExt = ["png", "jpg", "bmp", "jpeg"];
    for (let ext of imageExt) {
      try {
        let targetName = `${noExt(fileListStore.selectedFileName)}${imageTypeStore.imageType}.${ext}`;
        if (!fileListStore.selectedFileName.endsWith(".csv")) {
          const reg = new RegExp(`${ANNOTATED_SUFFIX}|${ORIGINAL_SUFFIX}$`);
          targetName = `${noExt(fileListStore.selectedFileName).replace(reg, `${imageTypeStore.imageType}`)}.${ext}`;
        }
        image = await storageResourceService.getFileBinaryByName(targetName, folderPath);
        await props.updateFileList(folderPath);
        break;
      } catch {
        continue;
      }
    }
    if (!loadingServiceMap.has(currentFetchId)) return;
    imageUrl.value = URL.createObjectURL(image!);
  } catch (ex: any) {
    if (ex.name === "AbortError") return;
    isShowImageMissing.value = true;
  } finally {
    loadingServiceMap.get(currentFetchId)?.close();
    loadingServiceMap.delete(currentFetchId);
    controller = null;
  }
};

watch([() => fileListStore.selectedFileName, () => fileListStore.refreshRequest, () => imageTypeStore.imageType], async ([newFileName, newRefreshRequest], [oldFileName]) => {
  const forceRefresh = newRefreshRequest.force;
  await fetchImageFile(newFileName, forceRefresh);

  const switchedFileGroup = stamp(newFileName) !== stamp(oldFileName);
  if (switchedFileGroup || forceRefresh) {
    magnifyingGlassStore.$reset();
    offsetX.value = 0;
    offsetY.value = 0;
    startX.value = 0;
    startY.value = 0;
  }
});

onUnmounted(() => {
  controller?.abort();
  loadingServiceMap.forEach((loadingService) => loadingService.close());
  imageUrlCache.forEach((cachedImageUrl) => URL.revokeObjectURL(cachedImageUrl));
  imageUrlCache.clear();
});
</script>

<style scoped lang="scss">
.image-preview {
  width: 100%;
  height: 100%;

  .image-preview-content {
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.05);
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;

    img {
      max-width: 100%;
      max-height: 100%;
      width: auto;
      height: auto;
      object-fit: contain;
      user-select: none;
      -webkit-user-select: none;
      -moz-user-select: none;
      -ms-user-select: none;
      -webkit-user-drag: none;
    }

    .images-missing {
      width: 309px;
      height: 259px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;

      .title {
        font-family: "Inter";
        font-weight: 600;
        font-style: "Semi Bold";
        font-size: 16px;
        line-height: 140%;
        letter-spacing: 1%;
        text-align: center;
        margin: 15px 0 2px;
      }

      .desc {
        font-family: "Inter";
        font-weight: 400;
        font-size: 14px;
        line-height: 140%;
        letter-spacing: 1%;
        text-align: center;
      }

      .refresh-btn {
        width: 110px;
        height: 40px;
        font-family: "Inter";
        font-weight: 600;
        font-size: 14px;
        margin-top: 15px;
        background-color: #ffffff;
        border: 1px solid rgba(229, 229, 229, 1);
        border-radius: 4px;
        cursor: pointer;
      }
    }

    .no-image {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      font-family: "Inter";
      font-weight: 400;
      font-size: 12px;
      color: #303030;
      line-height: 16px;
      text-align: center;

      .svg-icon {
        margin-bottom: 10px;
      }

      p {
        width: 219px;
      }
    }
  }
}
</style>
