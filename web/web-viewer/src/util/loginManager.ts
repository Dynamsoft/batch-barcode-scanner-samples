import { reactive } from "vue";
import oneDrive from "../tokenManagers/oneDrive";
import type { LoginMethod, LoginMethodMeta, OneDriveAuthInfo } from "./types";

class LoginManager {
  public loggedInAccounts: LoginMethodMeta[] = [];
  public isShowLoginPage: boolean;
  public isLoggedIn: boolean;

  constructor() {
    const storedMethodList = localStorage.getItem("BBS_Web_Viewer_LoggedIn_Method_List");
    if (storedMethodList) {
      const parsedMethodList = JSON.parse(storedMethodList) as LoginMethodMeta[];
      this.loggedInAccounts = parsedMethodList;
    }
    const firstScreen = localStorage.getItem("BBS_Web_Viewer_First_Screen");
    this.isLoggedIn = !!this.loggedInAccounts.length;
    this.isShowLoginPage = firstScreen === "Login" && !this.isLoggedIn;
  }

  public async login(method: LoginMethod) {
    if (method === "OneDrive") {
      await oneDrive.login();
      if (oneDrive.authInfo) {
        if (!this.loggedInAccounts.includes(oneDrive.authInfo)) {
          this.loggedInAccounts = this.loggedInAccounts.map((account) => ({ ...account, isActive: false }));
          this.loggedInAccounts.unshift(oneDrive.authInfo);
          localStorage.setItem("BBS_Web_Viewer_LoggedIn_Method_List", JSON.stringify(this.loggedInAccounts));
        }
      }
    }
  }

  public get activeLoginMethod() {
    const target = this.loggedInAccounts.find((account) => account.isActive);
    return target?.name;
  }

  public async switchAccount(account: LoginMethodMeta) {
    const index = this.loggedInAccounts.indexOf(account);
    if (index === -1) return;
    this.loggedInAccounts = this.loggedInAccounts.map((account) => ({ ...account, isActive: false }));
    this.loggedInAccounts[index]!.isActive = true;
    // const [item] = this.loggedInAccounts.splice(index, 1);
    // item!.isActive = true;
    // this.loggedInAccounts.unshift(item!);
    localStorage.setItem("BBS_Web_Viewer_LoggedIn_Method_List", JSON.stringify(this.loggedInAccounts));
    if (account.name === "OneDrive") {
      await oneDrive.setToken(account as OneDriveAuthInfo);
    }
  }

  public logout() {
    oneDrive.logout();
    this.loggedInAccounts = [];
    localStorage.removeItem("BBS_Web_Viewer_LoggedIn_Method_List");
  }
}

const loginManager = new LoginManager();

export default reactive(loginManager);
