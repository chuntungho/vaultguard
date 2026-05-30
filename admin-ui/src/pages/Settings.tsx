import { useEffect, useState } from "react";
import { useNotify, Title } from "react-admin";
import { Box, Card, CardContent, FormControlLabel, Switch, TextField as MuiTextField, Button, Alert } from "@mui/material";
import { adminActions } from "../dataProvider";
import type { AdminSettings } from "../types";

export function Settings() {
  const [settings, setSettings] = useState<AdminSettings | null>(null);
  const [saving, setSaving] = useState(false);
  const notify = useNotify();

  useEffect(() => {
    adminActions
      .getSettings()
      .then(setSettings)
      .catch((e: Error) => notify(e.message, { type: "error" }));
  }, [notify]);

  if (!settings) return <div>Loading…</div>;

  async function onSave() {
    setSaving(true);
    try {
      const updated = await adminActions.saveSettings(settings!);
      setSettings(updated);
      notify("Settings saved (in-memory)", { type: "success" });
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Box p={2}>
      <Title title="Settings" />
      <Card>
        <CardContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Settings changes are kept in memory only; restart the server to revert.
          </Alert>
          <MuiTextField
            label="Domain"
            fullWidth
            margin="normal"
            value={settings.domain}
            onChange={(e) => setSettings({ ...settings, domain: e.target.value })}
          />
          <FormControlLabel
            control={
              <Switch
                checked={settings.signupsAllowed}
                onChange={(e) => setSettings({ ...settings, signupsAllowed: e.target.checked })}
              />
            }
            label="Allow signups"
          />
          <FormControlLabel
            control={
              <Switch
                checked={settings.invitationsAllowed}
                onChange={(e) => setSettings({ ...settings, invitationsAllowed: e.target.checked })}
              />
            }
            label="Allow invitations"
          />
          <MuiTextField
            label="Password iterations"
            type="number"
            fullWidth
            margin="normal"
            value={settings.passwordIterations}
            disabled
            helperText="Read-only; changes apply at signup only."
          />
          <MuiTextField
            label="Mail from"
            fullWidth
            margin="normal"
            value={settings.mail.from}
            disabled
          />
          <Box mt={2}>
            <Button variant="contained" onClick={onSave} disabled={saving}>
              {saving ? "Saving…" : "Save"}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
