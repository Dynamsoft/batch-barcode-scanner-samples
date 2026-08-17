import { createApp } from "vue";
import "./style/common.scss";
import "element-plus/es/components/loading/style/index";
import "element-plus/es/components/message/style/index";
import "element-plus/es/components/message-box/style/index";
import App from "./App.vue";
import { createPinia } from "pinia";

const pinia = createPinia();
const app = createApp(App);
app.use(pinia);

app.mount("#app");
