module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-enum": [
      2,
      "always",
      [
        "deps",
        "mps-legacy-sync-plugin",
        "mps-diff-plugin",
        "generator",
        "mps-sync-plugin",
        "mps-sync-plugin-lib",
      ],
    ],
    "subject-case": [0, 'never'],
    "body-max-line-length": [0, 'always'],
    "footer-max-line-length": [0, 'always']
  },
  ignores: [
    (message) => message.includes('skip-lint')
  ],
};
