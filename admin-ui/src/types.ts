export const TOKEN_KEY = "vaultguardAdminToken";

export interface UserRow {
  id: string;
  name: string | null;
  email: string;
  enabled: boolean;
  emailVerified: boolean;
  twoFactorEnabled: boolean;
  createdAt: string;
  cipherCount: number;
  attachmentCount: number;
  organizations: { id: string; name: string }[];
}

export interface OrgRow {
  id: string;
  name: string;
  billingEmail: string | null;
  userCount: number;
  cipherCount: number;
  collectionCount: number;
}

export interface AdminSettings {
  domain: string;
  signupsAllowed: boolean;
  invitationsAllowed: boolean;
  passwordIterations: number;
  mail: { from: string; fromName: string };
}

export interface Diagnostics {
  version: string;
  javaVersion: string;
  javaVendor: string;
  osName: string;
  osArch: string;
  serverTime: string;
  domain: string;
}
