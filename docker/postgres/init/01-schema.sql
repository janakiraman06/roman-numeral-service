-- ============================================
-- Roman Numeral Service - Database Schema
-- ============================================
-- This script initializes the PostgreSQL schema.
-- Tables are created via JPA/Hibernate (ddl-auto: update),
-- but this script provides:
--   1. Performance indexes beyond JPA annotations
--   2. Seed data for testing
--   3. Database-level constraints
-- ============================================

-- ============================================
-- Extensions
-- ============================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- Seed Data: Demo User and API Key
-- ============================================
-- Note: Tables are created by JPA. This inserts seed data.
-- The demo API key is: rns_demo1234_testkeyforlocaldev
-- SHA-256 hash of the above key (for database storage)

-- We'll insert seed data after Hibernate creates the tables.
-- See 02-seed-data.sql which runs after app startup.

