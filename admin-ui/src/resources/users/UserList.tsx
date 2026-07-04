import {
  List,
  Datagrid,
  TextField,
  BooleanField,
  NumberField,
  DateField,
  SearchInput,
  DeleteButton,
  FunctionField,
  Pagination,
  useRecordContext,
  useRefresh,
  useNotify,
  Confirm,
} from "react-admin";
import { useState } from "react";
import { Button } from "@mui/material";
import { adminActions } from "../../dataProvider";
import type { UserRow } from "../../types";

const userFilters = [<SearchInput key="q" source="q" alwaysOn />];

function ToggleEnabledButton() {
  const record = useRecordContext<UserRow>();
  const refresh = useRefresh();
  const notify = useNotify();
  if (!record) return null;
  async function onClick() {
    try {
      if (record!.enabled) {
        await adminActions.disableUser(record!.id);
        notify("User disabled", { type: "info" });
      } else {
        await adminActions.enableUser(record!.id);
        notify("User enabled", { type: "info" });
      }
      refresh();
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    }
  }
  return (
    <Button size="small" onClick={onClick}>
      {record.enabled ? "Disable" : "Enable"}
    </Button>
  );
}

function DeauthButton() {
  const record = useRecordContext<UserRow>();
  const refresh = useRefresh();
  const notify = useNotify();
  const [open, setOpen] = useState(false);
  if (!record) return null;
  async function confirm() {
    try {
      await adminActions.deauthUser(record!.id);
      notify("Sessions cleared", { type: "info" });
      refresh();
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    } finally {
      setOpen(false);
    }
  }
  return (
    <>
      <Button size="small" onClick={() => setOpen(true)}>
        Deauth
      </Button>
      <Confirm
        isOpen={open}
        title="Deauthorize all sessions?"
        content={`This will sign ${record.email} out of all devices.`}
        onConfirm={confirm}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

function RemoveTwoFactorButton() {
  const record = useRecordContext<UserRow>();
  const refresh = useRefresh();
  const notify = useNotify();
  const [open, setOpen] = useState(false);
  if (!record) return null;
  async function confirm() {
    try {
      await adminActions.removeTwoFactor(record!.id);
      notify("Two-factor removed", { type: "info" });
      refresh();
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    } finally {
      setOpen(false);
    }
  }
  return (
    <>
      <Button size="small" disabled={!record.twoFactorEnabled} onClick={() => setOpen(true)}>
        Remove 2FA
      </Button>
      <Confirm
        isOpen={open}
        title="Remove two-factor?"
        content={`This will remove all 2FA methods from ${record.email}.`}
        onConfirm={confirm}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

export function UserList() {
  return (
    <List
      filters={userFilters}
      perPage={25}
      pagination={<Pagination rowsPerPageOptions={[25, 50, 100]} />}
      sort={{ field: "email", order: "ASC" }}
    >
      <Datagrid>
        <TextField source="email" />
        <TextField source="name" />
        <BooleanField source="enabled" />
        <BooleanField source="twoFactorEnabled" label="2FA" />
        <NumberField source="cipherCount" label="Ciphers" />
        <NumberField source="attachmentCount" label="Attachments" />
        <FunctionField<UserRow>
          label="Organizations"
          sortable={false}
          render={(record) => record.organizations?.map((o) => o.name).join(", ") ?? ""}
        />
        <DateField source="createdAt" />
        <ToggleEnabledButton />
        <DeauthButton />
        <RemoveTwoFactorButton />
        <DeleteButton mutationMode="pessimistic" />
      </Datagrid>
    </List>
  );
}
