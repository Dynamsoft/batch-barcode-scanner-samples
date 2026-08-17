<template>
  <div class="login-container">
    <header class="login-header">
      <img
        class="dynamsoft-logo"
        src="../assets/image/dynamsoft-logo.png"
        alt="dynamsoft-logo"
      />
    </header>
    <div class="login-main">
      <LoginMethodPanel :zIndex="2000" />
    </div>
  </div>
</template>

<script setup lang="ts">
import LoginMethodPanel from "./LoginMethodPanel.vue";
</script>

<style scoped lang="scss">
.login-container {
  width: 100%;
  height: 100%;

  .login-header {
    width: 100%;
    height: 64px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.2);
    display: flex;
    align-items: center;

    .dynamsoft-logo {
      width: 147px;
      height: 36px;
      margin-left: 24px;
    }
  }

  .login-main {
    width: 100%;
    height: calc(100% - 64px);
    position: relative;
  }
}
</style>
