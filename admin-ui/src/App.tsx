import { Admin, Resource, ListGuesser } from "react-admin";

export function App() {
  return (
    <Admin>
      <Resource name="users" list={ListGuesser} />
    </Admin>
  );
}
