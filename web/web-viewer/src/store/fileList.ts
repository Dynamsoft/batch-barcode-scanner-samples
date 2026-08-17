import { defineStore } from "pinia";
import { stamp, noExt, ANNOTATED_SUFFIX } from "../util";
import { useImageTypeStore } from "./imageType";

const storedVisitedIds = localStorage.getItem("visitedIds");
const parsedVisitedIds: string[] = storedVisitedIds ? JSON.parse(storedVisitedIds) : [];
export const useFileListStore = defineStore("fileList", {
  state: (): {
    list: any[];
    listLoading: boolean;
    refreshRequest: { id: number; force: boolean };
    selectedFileName: string;
    visitedIds: string[];
    activeFolderPath: string;
  } => {
    return {
      list: [],
      listLoading: true,
      selectedFileName: "",
      visitedIds: parsedVisitedIds,
      activeFolderPath: "",
      refreshRequest: {
        id: 0,
        force: false,
      },
    };
  },
  actions: {
    setFileList(newList: any[]) {
      const viewedIdSet = new Set(this.$state.visitedIds);
      this.$state.list.forEach((item: any) => {
        if (item.isViewed) {
          viewedIdSet.add(item.id);
        }
      });

      const firstFile = newList.find((item: any) => !item.folder);
      if (firstFile) {
        viewedIdSet.add(firstFile.id);
      }

      this.$state.visitedIds = [...viewedIdSet];

      this.$state.list = newList.map((newFile: any, index: number) => {
        return {
          ...newFile,
          isViewed: viewedIdSet.has(newFile.id) || index === 0 && !newFile.folder,
        };
      });
    },
    setSelectedFileName(fileName: string) {
      this.$state.selectedFileName = fileName;
    },
    setListLoading(loading: boolean) {
      this.$state.listLoading = loading;
    },
    setActiveFolderPath(folderPath: string) {
      this.$state.activeFolderPath = folderPath;
    },
    markViewed(item: any) {
      const itemId = item.id;
      if (!this.$state.visitedIds.includes(itemId)) {
        this.$state.visitedIds.push(itemId);
      }

      let targetItem;
      if (item.name.endsWith(".csv")) {
        targetItem = this.$state.list.find((listItem: any) => {
          return listItem.id !== itemId && noExt(listItem.name).endsWith(ANNOTATED_SUFFIX) && stamp(listItem.name) === stamp(item.name);
        });
      } else {
        targetItem = this.$state.list.find((listItem: any) => {
          return listItem.name.endsWith(".csv") && stamp(listItem.name) === stamp(item.name);
        });
      }
      if (targetItem && !this.$state.visitedIds.includes(targetItem.id)) {
        this.$state.visitedIds.push(targetItem.id);
      }

      this.$state.list = this.$state.list.map((item: any) => {
        if (item.id !== itemId && item.id !== targetItem?.id) return item;
        return {
          ...item,
          isViewed: true,
        };
      });

      localStorage.setItem("visitedIds", JSON.stringify(this.$state.visitedIds));
    },
    triggerRefresh(force: boolean = true) {
      this.$state.refreshRequest = {
        id: this.$state.refreshRequest.id + 1,
        force,
      };
    }
  },
  getters: {
    selectedImageFile(state) {
      let target;
      if (!state.selectedFileName.endsWith(".csv")) {
        target = state.list.find((file: any) => {
          return file.name === state.selectedFileName;
        });
      }
      const imageTypeStore = useImageTypeStore();
      target = state.list.find((file: any) => {
        return stamp(file.name) === stamp(state.selectedFileName) &&
          file.image &&
          (noExt(file.name).endsWith(imageTypeStore.imageType))
      });
      return target;
    },

    selectedCvsFile(state) {
      const target = state.list.find((file: any) => {
        return stamp(file.name) === stamp(state.selectedFileName) && file.name.endsWith(".csv");
      });
      return target;
    }
  }
});