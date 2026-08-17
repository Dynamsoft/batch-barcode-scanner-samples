import { BOX, DROP_BOX, GOOGLE_DRIVE, ONE_DRIVE, SHARE_POINT } from "../icons";
import type { LoginMethodMeta } from "./types";

export default [
  {
    name: "OneDrive",
    isAvailable: true,
    icon: ONE_DRIVE,
  },
  {
    name: "Google Drive",
    isAvailable: false,
    icon: GOOGLE_DRIVE,
  },
  {
    name: "SharePoint",
    isAvailable: false,
    icon: SHARE_POINT,
  },
  {
    name: "Dropbox",
    isAvailable: false,
    icon: DROP_BOX,
  },
  {
    name: "Box",
    isAvailable: false,
    icon: BOX,
  },
] as LoginMethodMeta[];
