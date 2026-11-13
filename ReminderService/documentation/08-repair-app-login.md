# Repair and verify the local application login

The schema constraint tests passed as root. Repeated reminder_app authentication failures remain unresolved. This helper changes only reminder_app@localhost and verifies a fresh connection to 127.0.0.1:3306/smartreminder with the exact newly chosen password.

File: C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\database\set-app-password.ps1

The helper is already saved; its complete source is in that file. Open it in VS Code to inspect it; Ctrl+S saves edits. No source edits are needed. Its PowerShell syntax was checked by the assistant, but live password-changing behavior has not been executed by the assistant.

## Run in ordinary PowerShell

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\database\set-app-password.ps1'
```

The execution-policy setting applies only to this new PowerShell process. Windows administrator access is not required; MySQL root credentials authorize the account change.

## Prompt order

1. Enter the EXISTING root password saved during recovery. The helper verifies it immediately, before asking for a new password.
2. Only after ROOT LOGIN VERIFIED, choose a NEW reminder_app password using 12-128 characters from letters, numbers, and ! @ # $ % ^ & * _ = + . , : ? - . Save it privately as Local MySQL - reminder_app.
3. Repeat that NEW application password.

All three prompts use Read-Host -AsSecureString. Root authentication is verified first. The last two entries are compared without displaying them. The password character restriction keeps SQL quoting and MySQL option-file parsing unambiguous in this beginner helper.

## How it works

The helper creates a unique temporary folder under LOCALAPPDATA/Temp and restricts its permissions to the current Windows account and SYSTEM. It writes a root verification option file, then after successful root authentication writes a password-change SQL file and an application verification option file there. These files contain the chosen password temporarily; it is not encrypted on disk, which is why access is restricted and the files are removed in finally on normal exit/failure paths. Forced termination can bypass normal cleanup.

Root authentication uses a private temporary option file populated from the hidden PowerShell prompt. Double quotes and backslashes are escaped for option-file parsing. The helper checks CURRENT_USER() before any account-changing SQL runs. Root credentials are not included in command-line arguments. The password-change SQL is read through SOURCE instead of including its contents on the process command line. Application verification reads the private option file; no password is placed in the command-line arguments. Native exit codes and the exact authenticated account/database are checked before reporting success. Temporary credential files and the empty directory are then removed.

The helper neither stops MySQL nor resets root, changes schema objects, grants extra privileges, nor launches Spring Boot. It is a local recovery utility, not application authentication code.

## Expected output

```text
APPLICATION LOGIN VERIFIED
Account: reminder_app@localhost
Database: smartreminder
```

These are expected results until the learner executes the helper. Share only these nonsecret lines or an error. If root authentication fails, the helper stops before asking for a new application password, and no application password is changed. If application verification fails or returns another account, the helper throws an error rather than claiming success.

After success, future manual application logins and start-local.ps1 must use the NEW password chosen at steps 2 and 3. No additional password reset should be needed just to run the constraint tests or start the backend.

References: https://dev.mysql.com/doc/refman/8.0/en/option-files.html and https://dev.mysql.com/doc/refman/8.0/en/password-security-user.html

