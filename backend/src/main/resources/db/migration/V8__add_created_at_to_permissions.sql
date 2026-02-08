-- V8: Add created_at column to permissions table
ALTER TABLE permissions ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
