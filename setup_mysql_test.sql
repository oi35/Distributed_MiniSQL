-- Setup MySQL for RegionServer Testing

-- Create test database
CREATE DATABASE IF NOT EXISTS minisql;

-- Create test user with password
CREATE USER IF NOT EXISTS 'minisql_test'@'localhost' IDENTIFIED BY 'test_password';

-- Grant all privileges on minisql database
GRANT ALL PRIVILEGES ON minisql.* TO 'minisql_test'@'localhost';

-- Also grant to root with password 'password' for compatibility
ALTER USER 'root'@'localhost' IDENTIFIED BY 'password';

-- Flush privileges
FLUSH PRIVILEGES;

-- Show databases
SHOW DATABASES;

-- Show users
SELECT User, Host FROM mysql.user WHERE User IN ('root', 'minisql_test');
