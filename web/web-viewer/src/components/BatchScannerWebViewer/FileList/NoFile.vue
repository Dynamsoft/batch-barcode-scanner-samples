<template>
  <div class="no-file">
    <div class="file-list-scroll-area" :class="{ 'has-docked-card': showMobileAppButton && isAppDownloadCardVisible }">
      <div v-if="showNoFilesMessage" class="no-file-text">
        <div v-html="NO_FILES_UPLOADED_YET" class="svg-icon flex-center"></div>
        <span>No files uploaded yet.</span>
      </div>
      <div v-if="showEmptyState && !samplesVisible" class="show-sample-file" @click="emit('showSampleFiles')">
        <div v-html="FILE_READ" class="svg-icon flex-center"></div>
        <span>Show sample files</span>
        <div v-html="SHOW_SAMPLE_FILES" class="show-sample-files-icon flex-center"></div>
      </div>
      <slot name="file-list" />
      <div class="tip" v-show="showEmptyState && isShowTip">
        <div class="svg-icon" v-html="TIP"></div>
        <p>
          {{ loginManager.isLoggedIn ? "Please upload your scan results to your cloud storage in the Batch Barcode Scanner app." : "Authorize access to view the files you uploaded from the Barcode Batch Scan app." }}
        </p>
        <div class="svg-icon close" @click="isShowTip = false" v-html="REMOVE"></div>
      </div>
      <div v-if="showEmptyState || isAppDownloadCardVisible" class="app-download-card" :class="{ 'is-docked': showMobileAppButton }">
        <p class="title">Batch Barcode Scanner</p>
        <p class="desc">Ready-to-use app for iOS and Android — with near 100% scanning accuracy.</p>
        <div class="platform">
          <a href="https://play.google.com/store/apps/details?id=com.dynamsoft.bbs.app" target="_blank">
            <div v-html="GOOGLE_STORE"></div>
          </a>
          <a href="https://apps.apple.com/us/app/dynamsoft-batchbarcodescanner/id6751793075" target="_blank">
            <div v-html="APPLE_STORE"></div>
          </a>
        </div>
      </div>
    </div>
    <button v-if="showMobileAppButton" class="download-mobile-app-btn" type="button" @click="isAppDownloadCardVisible = true">
      <span>Download Mobile App</span>
      <div v-html="DOWNLOAD_2" class="icon-svg flex-center"></div>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { APPLE_STORE, FILE_READ, GOOGLE_STORE, NO_FILES_UPLOADED_YET, REMOVE, SHOW_SAMPLE_FILES, TIP, DOWNLOAD_2 } from "../../../icons";
import loginManager from "../../../util/loginManager";

defineProps<{
  samplesVisible: boolean;
  showEmptyState: boolean;
  showNoFilesMessage: boolean;
  showMobileAppButton: boolean;
}>();

const emit = defineEmits<{
  showSampleFiles: [];
}>();

const isShowTip = ref(true);
const isAppDownloadCardVisible = ref(false);
</script>

<style scoped lang="scss">
.no-file {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .file-list-scroll-area {
    min-height: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow-y: auto;
    overflow-x: hidden;
    scrollbar-width: thin;
    scrollbar-color: rgba(194, 198, 206, 1) rgba(0, 0, 0, 0);

    &.has-docked-card {
      margin-bottom: 273px;
    }
  }

  .no-file-text,
  .show-sample-file {
    width: 100%;
    height: 50px;
    display: flex;
    align-items: center;
    font-family: "Inter";
    font-weight: 600;
    font-size: 14px;
    border-bottom: 1px solid #0000001a;

    .svg-icon {
      margin-right: 10px;
    }

    .show-sample-files-icon {
      margin-left: auto;
      flex-shrink: 0;
    }
  }

  .show-sample-file {
    color: #666666;
    cursor: pointer;
  }

  .tip {
    width: 100%;
    min-height: 117px;
    height: auto;
    flex-shrink: 0;
    background-color: #f4f4f5;
    margin: 50px auto;
    font-family: "Inter";
    font-weight: 400;
    font-size: 14px;
    color: #909399;
    line-height: 22px;
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 13px 16px;

    .svg-icon {
      height: 100%;
    }

    p {
      height: auto;
      margin: 0 8px;
    }

    .close {
      :deep(svg) {
        cursor: pointer;
      }
    }
  }

  .app-download-card {
    width: 249px;
    max-width: 100%;
    height: 263px;
    border: 1px solid #0000001a;
    border-radius: 8px;
    padding: 30px 22px;
    margin: auto auto 10px;
    flex-shrink: 0;

    &.is-docked {
      position: absolute;
      left: 50%;
      bottom: 45px;
      z-index: 1;
      margin: 0;
      transform: translateX(-50%);
      background-color: #ffffff;
    }

    .title {
      font-family: "Inter";
      font-weight: 600;
      font-size: 16px;
      margin-bottom: 6px;
    }

    .desc {
      font-family: "Inter";
      font-weight: 400;
      font-size: 14px;
    }

    .platform {
      display: flex;
      flex-direction: column;
      gap: 15px;
      margin-top: 30px;

      svg {
        cursor: pointer;
      }
    }
  }

  .download-mobile-app-btn {
    width: 100%;
    height: 40px;
    font-family: "Inter";
    font-weight: 500;
    font-size: 14px;
    border: 1px solid #0000001a;
    display: flex;
    justify-content: center;
    align-items: center;
    border-radius: 4px;
    cursor: pointer;
    background-color: #ffffff;
    flex-shrink: 0;
    margin-top: 5px;
    margin-bottom: 0;

    .icon-svg {
      margin-left: 7px;
      vertical-align: middle;
    }
  }
}
</style>
