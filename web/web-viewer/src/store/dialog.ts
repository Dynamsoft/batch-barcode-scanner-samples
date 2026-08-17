import { defineStore } from "pinia";
import loginManager from "../util/loginManager";

export const useDialogStore = defineStore("dialog", {
  state: (): {
    reAuthDialog: boolean;
    continueMethodDialog: boolean;
  } => {
    const firstScreen = localStorage.getItem("BBS_Web_Viewer_First_Screen");
    return {
      reAuthDialog: false,
      continueMethodDialog: firstScreen !== "Login" && !loginManager.isLoggedIn,
    };
  },
  actions: {
    setDialogVisible(visible: Partial<ReturnType<typeof useDialogStore>["$state"]>) {
      Object.assign(this.$state, visible);
    },
  },
});
