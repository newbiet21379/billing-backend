-- Database initialization script for billing application
-- This script creates necessary extensions and sets up the database schema

-- Create UUID extension for generating UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create schema version table for tracking migrations
CREATE TABLE IF NOT EXISTS schema_version (
    version VARCHAR(20) PRIMARY KEY,
    description TEXT,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert initial version
INSERT INTO schema_version (version, description)
VALUES ('1.0.0', 'Initial database setup')
ON CONFLICT (version) DO NOTHING;

-- Create indexes for better performance
-- These will be created by Hibernate/JPA based on entity definitions
-- Additional performance tuning indexes can be added here

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO billing_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO billing_user;