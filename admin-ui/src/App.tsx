import { Admin, Resource, CustomRoutes } from "react-admin";
import { Route } from "react-router-dom";
import { dataProvider } from "./dataProvider";
import { authProvider } from "./authProvider";
import { Layout } from "./Layout";
import { i18nProvider } from "./i18n";
import { LoginPage } from "./pages/LoginPage";
import { Settings } from "./pages/Settings";
import { Diagnostics } from "./pages/Diagnostics";
import { UserList } from "./resources/users/UserList";
import { OrgList } from "./resources/organizations/OrgList";

export function App() {
  return (
    <Admin
      title="VaultGuard Admin"
      dataProvider={dataProvider}
      authProvider={authProvider}
      loginPage={LoginPage}
      layout={Layout}
      i18nProvider={i18nProvider}
      disableTelemetry
    >
      <Resource name="users" list={UserList} options={{ label: "Users" }} />
      <Resource name="organizations" list={OrgList} options={{ label: "Organizations" }} />
      <CustomRoutes>
        <Route path="/settings" element={<Settings />} />
        <Route path="/diagnostics" element={<Diagnostics />} />
      </CustomRoutes>
    </Admin>
  );
}
