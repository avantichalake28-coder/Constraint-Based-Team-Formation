import ReactDOM from "react-dom/client";
import { StrictMode } from "react";
import { App } from "./app/App";
import "./styles/index.css";
import "./styles/student-app.css";
import "./styles/welcome.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
