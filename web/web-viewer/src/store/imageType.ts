import { defineStore } from "pinia";
import { useFileListStore } from "./fileList";
import { ANNOTATED_SUFFIX, stamp } from "../util";
import type { OriginalOrAnnotated } from "../util/types";

export const useImageTypeStore = defineStore("imageType", {
  state: (): {
    imageType: OriginalOrAnnotated;
  } => {
    return {
      imageType: ANNOTATED_SUFFIX
    };
  },
  actions: {
    setImageType(newImageType: OriginalOrAnnotated) {
      this.$state.imageType = newImageType;
    }
  },
  getters: {
    hasCurrentType: (state) => {
      const fileListStore = useFileListStore();
      const target = fileListStore.list.find((fileItem: any) => {
        return stamp(fileItem.name) === stamp(fileListStore.selectedFileName) && fileItem.name.includes(state.imageType);
      })
      return !!target;
    }
  }
});