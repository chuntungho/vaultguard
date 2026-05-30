import { Layout as RALayout, Menu, MenuItemLink } from "react-admin";
import type { LayoutProps } from "react-admin";
import PeopleIcon from "@mui/icons-material/People";
import BusinessIcon from "@mui/icons-material/Business";
import SettingsIcon from "@mui/icons-material/Settings";
import InfoIcon from "@mui/icons-material/Info";

function AdminMenu() {
  return (
    <Menu>
      <MenuItemLink to="/users" primaryText="Users" leftIcon={<PeopleIcon />} />
      <MenuItemLink to="/organizations" primaryText="Organizations" leftIcon={<BusinessIcon />} />
      <MenuItemLink to="/settings" primaryText="Settings" leftIcon={<SettingsIcon />} />
      <MenuItemLink to="/diagnostics" primaryText="Diagnostics" leftIcon={<InfoIcon />} />
    </Menu>
  );
}

export function Layout(props: LayoutProps) {
  return <RALayout {...props} menu={AdminMenu} />;
}
