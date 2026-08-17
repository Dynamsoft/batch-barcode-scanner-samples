import { defineStore } from "pinia";

export const useRotateAngleStore = defineStore("rotateAngle", {
  state: (): {
    angle: number;
  } => {
    return {
      angle: 0
    };
  },
  actions: {
    setAngle(newAngle: number) {
      this.$state.angle = newAngle;
    }
  }
});