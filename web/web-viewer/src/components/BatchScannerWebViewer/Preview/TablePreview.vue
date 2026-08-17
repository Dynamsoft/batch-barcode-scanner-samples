<template>
  <div class="result-table" ref="resultTableRef">
    <div class="table-container" v-show="!isShowCsvMissing">
      <el-table :data="csvData" style="width: 100%" height="100%" row-class-name="csv-list-row" header-cell-class-name="csv-list-header">
        <el-table-column prop="INDEX" label="Index" />
        <el-table-column prop="BARCODE_TEXT" label="Barcode Text" />
        <el-table-column prop="BARCODE_FORMAT" label="Barcode Format" />
        <el-table-column prop="STATUS" label="Status" />
        <el-table-column prop="LOCATION" label="Location" />
        <template #empty>
          <div v-html="NO_FILES" class="flex-center"></div>
          <p>No tables uploaded from the Batch Barcode Scanner app yet.</p>
        </template>
      </el-table>
    </div>
    <div class="cvs-file-missing" v-show="isShowCsvMissing">
      <div class="icon-svg flex-center" v-html="CSV_MISSING"></div>
      <span class="title">CSV File Missing</span>
      <span class="desc">This package is missing CSV file. Please refresh and try again.</span>
      <button class="refresh-btn" @click="refresh">Refresh</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, useTemplateRef, watch } from "vue";
import { useFileListStore } from "../../../store/fileList";
import { ElLoading } from "element-plus";
import { ANNOTATED_SUFFIX, noExt, ORIGINAL_SUFFIX, tableRowsToObjects } from "../../../util";
import storageResourceService from "../../../util/storageResourceService";
import Papa from "papaparse";
import { CSV_MISSING, NO_FILES } from "../../../icons";

const props = defineProps<{
  updateFileList: (folderPath?: string) => Promise<void>;
}>();

const fileListStore = useFileListStore();
const isShowCsvMissing = ref(false);
const resultTableRef = useTemplateRef("resultTableRef");
const csvData = ref<Record<string, string>[]>([]);
const csvDataCache = new Map<string, Record<string, string>[]>();
let fetchId = 0;
let loadingServiceMap: Map<number, ReturnType<typeof ElLoading.service>> = new Map();
let controller: AbortController | null = null;

const fetchCsvFile = async (newFileName: string, forceRefresh: boolean) => {
  const currentFetchId = ++fetchId;

  try {
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
      isShowCsvMissing.value = false;
      csvData.value = [];
      return;
    }

    const csvFile = fileListStore.selectedCvsFile;
    if (!csvFile) {
      throw new Error("CSV not found");
    }
    const csvFileId = String(csvFile.id);

    if (forceRefresh) {
      csvDataCache.delete(csvFileId);
    } else {
      const cachedCsvData = csvDataCache.get(csvFileId);
      if (cachedCsvData) {
        isShowCsvMissing.value = false;
        csvData.value = cachedCsvData;
        return;
      }
    }

    isShowCsvMissing.value = false;
    loadingServiceMap.set(
      currentFetchId,
      ElLoading.service({
        lock: true,
        text: "Loading...",
        background: "rgba(0, 0, 0, 0.7)",
        target: resultTableRef.value!,
      }),
    );

    controller = new AbortController();
    const csv = await storageResourceService.getFileBinary(csvFileId, forceRefresh, controller);

    if (currentFetchId !== fetchId) return;
    if (!loadingServiceMap.has(currentFetchId)) return;

    Papa.parse<string[]>(csv as File, {
      complete: (results) => {
        if (currentFetchId !== fetchId) return;
        if (results.data) {
          const parsedCsvData = tableRowsToObjects(results.data);
          csvDataCache.set(csvFileId, parsedCsvData);
          csvData.value = parsedCsvData;
        }
      },
    });
  } catch (ex: any) {
    if (ex.name === "AbortError") {
      return;
    }
    isShowCsvMissing.value = true;
    csvData.value = [];
  } finally {
    loadingServiceMap.get(currentFetchId)?.close();
    loadingServiceMap.delete(currentFetchId);
    if (currentFetchId === fetchId) {
      controller = null;
    }
  }
};

const refresh = async () => {
  isShowCsvMissing.value = false;
  const currentFetchId = ++fetchId;
  const folderPath = fileListStore.activeFolderPath;
  try {
    loadingServiceMap.set(
      currentFetchId,
      ElLoading.service({
        lock: true,
        text: "Loading",
        background: "rgba(0, 0, 0, 0.7)",
        target: resultTableRef.value!,
      }),
    );
    const reg = new RegExp(`${ANNOTATED_SUFFIX}|${ORIGINAL_SUFFIX}$`);
    const csv = await storageResourceService.getFileBinaryByName(noExt(fileListStore.selectedFileName).replace(reg, "") + ".csv", folderPath);
    await props.updateFileList(folderPath);
    if (!loadingServiceMap.has(currentFetchId)) return;
    Papa.parse<string[]>(csv as File, {
      complete: (results) => {
        if (results.data) {
          csvData.value = tableRowsToObjects(results.data);
        }
      },
    });
  } catch (ex: any) {
    if (ex.name === "AbortError") return;
    isShowCsvMissing.value = true;
  } finally {
    loadingServiceMap.get(currentFetchId)?.close();
    loadingServiceMap.delete(currentFetchId);
  }
};

watch([() => fileListStore.selectedFileName, () => fileListStore.refreshRequest], async ([newFileName, newRefreshRequest]) => {
  const forceRefresh = newRefreshRequest.force;
  await fetchCsvFile(newFileName, forceRefresh);
});
</script>

<style scoped lang="scss">
.result-table {
  width: 100%;
  height: 100%;
  border: 1px solid rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: center;
  align-items: center;

  .table-container {
    width: 100%;
    height: 100%;
    overflow: hidden;

    :deep(.csv-list-header) {
      background-color: #f2f2f2;
      font-family: "Inter";
      font-weight: 400;
      font-size: 14px;
      line-height: 20px;
      letter-spacing: 0%;
      vertical-align: middle;
      color: rgba(0, 0, 0, 0.8);
    }

    :deep(.csv-list-row) {
      font-family: "Inter";
      font-weight: 400;
      font-size: 12px;
      line-height: 16px;
      letter-spacing: 0%;
      vertical-align: middle;
      color: rgba(0, 0, 0, 0.8);
    }

    :deep(.dynamsoft-table__empty-text) {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      line-height: unset;

      .icon-svg {
        margin-bottom: 10px;
      }

      p {
        width: 187px;
        font-family: "Inter";
        font-weight: 400;
        font-size: 12px;
        text-align: center;
        color: #333333;
      }
    }
  }

  .cvs-file-missing {
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
      vertical-align: middle;
      margin-top: 15px;
      background-color: #ffffff;
      border: 1px solid rgba(229, 229, 229, 1);
      border-radius: 4px;
      cursor: pointer;
    }
  }
}
</style>
