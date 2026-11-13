-- Official image initially grants MYSQL_USER schema privileges; narrow the runtime account.
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'reminder_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON smartreminder.* TO 'reminder_app'@'%';
