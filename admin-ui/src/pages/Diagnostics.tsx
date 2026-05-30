import { useEffect, useState, useCallback } from "react";
import { useNotify, Title } from "react-admin";
import { Box, Card, CardContent, Table, TableBody, TableCell, TableRow, Button } from "@mui/material";
import { adminActions } from "../dataProvider";
import type { Diagnostics as DiagnosticsData } from "../types";

export function Diagnostics() {
  const [data, setData] = useState<DiagnosticsData | null>(null);
  const notify = useNotify();

  const load = useCallback(() => {
    adminActions
      .getDiagnostics()
      .then(setData)
      .catch((e: Error) => notify(e.message, { type: "error" }));
  }, [notify]);

  useEffect(() => {
    load();
  }, [load]);

  if (!data) return <div>Loading…</div>;

  return (
    <Box p={2}>
      <Title title="Diagnostics" />
      <Card>
        <CardContent>
          <Box mb={1}>
            <Button onClick={load} variant="outlined" size="small">
              Refresh
            </Button>
          </Box>
          <Table>
            <TableBody>
              {Object.entries(data).map(([k, v]) => (
                <TableRow key={k}>
                  <TableCell sx={{ fontWeight: 600, width: "30%" }}>{k}</TableCell>
                  <TableCell>{String(v)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </Box>
  );
}
