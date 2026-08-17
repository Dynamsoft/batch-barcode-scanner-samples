import { defineStore } from "pinia";

export const useMagnifyingGlassStore = defineStore("magnifyingGlass", {
  state: (): {
    zoom: number;
  } => {
    return {
      zoom: 1
    };
  },
  actions: {
    setZoom(newZoom: number) {
      if (newZoom > 5) {
        newZoom = 5;
      } else if (newZoom < 0.5) {
        newZoom = 0.5;
      }
      this.$state.zoom = newZoom;
    }
  }
});