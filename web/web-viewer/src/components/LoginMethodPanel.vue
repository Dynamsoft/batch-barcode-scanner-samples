<template>
  <div class="login-method-container" :style="{ zIndex: props.zIndex ? props.zIndex : 99999 }">
    <div class="mask" v-show="props.isShowMask" @click="close"></div>
    <div class="login-method-panel">
      <div v-html="CLOSE" @click="close" v-show="props.isShowMask" class="icon closeBtn"></div>
      <div class="title">Batch Barcode Scanner Web Viewer</div>
      <div class="desc">
        <span>Connect your cloud or network storage for real-time scan results.</span>
        <br />
        <span>One-time setup — we never store your credentials.</span>
      </div>
      <div class="login-method-options">
        <div class="login-method-option" v-for="method in methodList" :key="method.name" @click="login(method)" :style="{ opacity: method.isAvailable ? 1 : 0.5, cursor: method.isAvailable ? 'pointer' : 'not-allowed' }">
          <div class="option-content">
            <div class="icon" v-html="method.icon"></div>
            {{ method.name }}
          </div>
        </div>
      </div>
      <span v-if="props.showContinueAsGuest" class="to-dashboard" @click="toDashboard">
        <span>Continue as a guest</span>
        <div v-html="CARET_RIGHT" class="flex-center caret-right"></div>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElLoading } from "element-plus";
import methodList from "../util/methodList";
import type { LoginMethod, LoginMethodMeta } from "../util/types";
import loginManager from "../util/loginManager";
import { useDialogStore } from "../store/dialog.ts";
import { CARET_RIGHT, CLOSE } from "../icons.ts";

const props = withDefaults(
  defineProps<{
    isShowMask?: boolean;
    zIndex?: number;
    showContinueAsGuest?: boolean;
    updateLoginMethodPanelVisibility?: (visible: boolean) => void;
  }>(),
  {
    showContinueAsGuest: true,
  },
);

const dialogStore = useDialogStore();

const login = async (method: LoginMethodMeta) => {
  if (!method.isAvailable) return;
  let loading = ElLoading.service({
    lock: true,
    text: "Logging in...",
    background: "rgba(0, 0, 0, 0.7)",
  });
  try {
    await loginManager.login(method.name as LoginMethod);
    loginManager.isShowLoginPage = false;
    loginManager.isLoggedIn = !!loginManager.loggedInAccounts.length;
    dialogStore.setDialogVisible({ continueMethodDialog: false });
  } catch (error) {
    alert(`${method.name} login failed. Please try again.`);
  } finally {
    loading.close();
    props.updateLoginMethodPanelVisibility?.(false);
  }
};

const toDashboard = () => {
  loginManager.isShowLoginPage = false;
  localStorage.setItem("BBS_Web_Viewer_First_Screen", "Dashboard");
  dialogStore.setDialogVisible({ continueMethodDialog: false });
};

const close = () => {
  props.updateLoginMethodPanelVisibility?.(false);
};
</script>

<style scoped lang="scss">
.login-method-container {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;

  .mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.5);
  }

  .login-method-panel {
    width: 720px;
    height: 500px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    position: absolute;
    top: 40%;
    left: 50%;
    transform: translate(-50%, -50%);
    background-color: #ffffff;
    padding: 0 10px;

    .closeBtn {
      position: absolute;
      top: 20px;
      right: 20px;
      display: flex;
      justify-content: center;
      align-items: center;
      cursor: pointer;
    }

    .title {
      width: 277px;
      font-family: "Inter";
      font-size: 24px;
      font-weight: 600;
      text-align: center;
    }

    .desc {
      font-family: "Inter";
      text-align: center;
      font-size: 14px;
      margin: 8px 0 35px 0;
    }

    .login-method-options {
      width: 360px;
      height: 232px;
      display: flex;
      flex-direction: column;

      .login-method-option {
        width: 100%;
        flex: 1;
        background-color: rgba(0, 0, 0, 0.05);
        font-family: "Inter";
        font-weight: 600;
        display: flex;
        justify-content: center;
        align-items: center;
        margin-bottom: 8px;
        font-size: 14px;
        cursor: pointer;

        &:last-child {
          margin-bottom: 0;
        }

        .option-content {
          width: 35%;
          display: flex;
          align-items: center;

          .icon {
            margin-right: 10px;
          }
        }
      }
    }

    .to-dashboard {
      font-family: "Inter";
      font-weight: 400;
      font-size: 14px;
      color: #606266;
      margin-top: 30px;
      display: flex;
      align-items: center;
      cursor: pointer;

      .caret-right {
        margin-left: 5px;
      }
    }
  }
}
</style>
