import nextPlugin from "@next/eslint-plugin-next";
import tseslint from "typescript-eslint";
import tsParser from "@typescript-eslint/parser";

export default [
  { ignores: [".next/", "node_modules/"] },
  ...tseslint.configs.recommended,
  {
    files: ["**/*.ts", "**/*.tsx"],
    languageOptions: { parser: tsParser },
    plugins: { "@next/next": nextPlugin },
    rules: {
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs["core-web-vitals"].rules,
      "@typescript-eslint/no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
    },
  },
];
