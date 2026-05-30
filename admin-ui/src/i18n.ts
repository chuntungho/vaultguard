import polyglotI18nProvider from "ra-i18n-polyglot";
import englishMessages from "ra-language-english";

const messages = {
  ...englishMessages,
  resources: {
    users: { name: "User |||| Users" },
    organizations: { name: "Organization |||| Organizations" },
  },
};

export const i18nProvider = polyglotI18nProvider(() => messages, "en", [
  { locale: "en", name: "English" },
]);
