-- Database initialization script for Billing System
-- This script is automatically executed when PostgreSQL container starts

-- Ensure proper database encoding
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create additional indexes for performance optimization (will be created if not exists)
-- These are prepared for future table creation by the application

-- Set up default timezone and configurations
SET timezone = 'UTC';

-- Log successful initialization
DO $$
BEGIN
    RAISE NOTICE 'Billing database initialized successfully';
END $$;