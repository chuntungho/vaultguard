import {
  List,
  Datagrid,
  TextField,
  NumberField,
  SearchInput,
  DeleteButton,
  Pagination,
} from "react-admin";

const orgFilters = [<SearchInput key="q" source="q" alwaysOn />];

export function OrgList() {
  return (
    <List
      filters={orgFilters}
      perPage={25}
      pagination={<Pagination rowsPerPageOptions={[25, 50, 100]} />}
      sort={{ field: "name", order: "ASC" }}
    >
      <Datagrid>
        <TextField source="name" />
        <TextField source="billingEmail" />
        <NumberField source="userCount" label="Users" />
        <NumberField source="cipherCount" label="Ciphers" />
        <NumberField source="collectionCount" label="Collections" />
        <DeleteButton mutationMode="pessimistic" />
      </Datagrid>
    </List>
  );
}
