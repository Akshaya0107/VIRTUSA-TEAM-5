# Local MySQL database and runtime account

Status: prepared instructions only. The assistant has not executed these SQL statements or tested this account. Learner verification is pending.

## Names

- Database: smartreminder
- Runtime account: reminder_app@localhost
- Host and port for local development: 127.0.0.1:3306

This account is local-development-only. Docker accounts and hosts will be configured separately during container deployment. The application will not use root.

## 1. Open the MySQL client from PowerShell

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -h 127.0.0.1 -P 3306 -u root -p
```

Enter the recovered root password privately at the prompt. Wait for mysql> before entering SQL. SQL does not belong at the PS prompt.

## 2. Create the database from the saved script

File: C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\database\01-create-database.sql

The file is already saved. To inspect it, open it in VS Code. Any edit is saved with Ctrl+S.

Complete file content:

```sql
-- Execute as a MySQL administrator. This file contains no passwords.
-- If this database already exists, stop and inspect it rather than replacing it.
CREATE DATABASE smartreminder
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

At mysql>, run:

```sql
SOURCE C:/Users/avina/OneDrive/Documents/ChatGPT/study/ReminderService/database/01-create-database.sql;
```

SOURCE executes a SQL file. Forward slashes work in this client path. CREATE DATABASE creates an empty database, not tables. utf8mb4 supports Unicode, including emoji. The default collation compares text without distinguishing case or accents; specific identity/token columns will need deliberate comparison rules when migrations are implemented. If the database already exists, this script fails instead of altering it.

## 3. Create the runtime account privately

Choose a different strong password from the root password. Keep it in a password manager. Replace the placeholder before executing, and keep the surrounding straight single quotes. For this beginner exercise, avoid a single quote or backslash within the password, so SQL escaping is unnecessary. Do not save the actual password in this repository or share the command with its password here.

Run at mysql>:

```sql
CREATE USER 'reminder_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_YOUR_APP_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON smartreminder.* TO 'reminder_app'@'localhost';
exit;
```

CREATE USER creates a database-server account, not an application user in our future users table. The host component limits matching to local connections. The GRANT statement permits reading, inserting, updating, and deleting rows only in smartreminder. It does not permit creating databases, changing table structures, or creating accounts. Account-management statements take effect directly; FLUSH PRIVILEGES is unnecessary.

If the account already exists or password validation rejects the password, stop and share the error without the password. Do not drop an existing account blindly.

Schema migrations will use an administrator or a separate migration account. Hibernate will validate the schema rather than alter it through this restricted runtime account. No JPA dependency or connection settings have been added yet.

## 4. Verify as the application account

At PowerShell:

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -h 127.0.0.1 -P 3306 -u reminder_app -p smartreminder
```

Enter the application account password, not the root password, at the prompt. Then run at mysql>:

```sql
SELECT DATABASE() AS database_name, CURRENT_USER() AS authenticated_account;
SHOW GRANTS FOR CURRENT_USER;
SHOW TABLES;
```

Expected: smartreminder and reminder_app@localhost; schema-scoped SELECT, INSERT, UPDATE, DELETE grants (plus USAGE, which grants no global data privileges); no tables yet. These are expected outputs until the learner runs them, not claimed test results. Share only verification outputs, not the CREATE USER command with its password.

Return to PowerShell using:

```sql
exit;
```

## References

- https://dev.mysql.com/doc/refman/8.0/en/create-database.html
- https://dev.mysql.com/doc/refman/8.0/en/create-user.html
- https://dev.mysql.com/doc/refman/8.0/en/grant.html
