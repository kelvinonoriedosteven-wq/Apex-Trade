-- ApexTrade Enterprise Brokerage PostgreSQL Companion Schema
-- Mirrors live Firestore collections for analytical warehousing, tax reporting, and double-entry auditing.

CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    cash_balance_usd NUMERIC(18, 6) NOT NULL DEFAULT 0 CHECK (cash_balance_usd >= 0),
    total_deposited_usd NUMERIC(18, 6) NOT NULL DEFAULT 0,
    pending_withdrawal_usd NUMERIC(18, 6) NOT NULL DEFAULT 0 CHECK (pending_withdrawal_usd >= 0),
    kyc_status VARCHAR(32) NOT NULL DEFAULT 'not_submitted',
    account_status VARCHAR(32) NOT NULL DEFAULT 'active',
    referral_code VARCHAR(32) NOT NULL UNIQUE,
    referred_by VARCHAR(32),
    two_factor_pin_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Immutable double-entry ledger (append-only)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES users(user_id),
    entry_type VARCHAR(48) NOT NULL,
    direction VARCHAR(16) NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT', 'SETTLE')),
    amount_usd NUMERIC(18, 6) NOT NULL CHECK (amount_usd >= 0),
    balance_before_usd NUMERIC(18, 6) NOT NULL,
    balance_after_usd NUMERIC(18, 6) NOT NULL,
    reference_type VARCHAR(48) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    actor_uid VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ledger_user_created ON ledger_entries(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS trades (
    id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES users(user_id),
    symbol VARCHAR(32) NOT NULL,
    asset_class VARCHAR(24) NOT NULL,
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type VARCHAR(16) NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT')),
    quantity NUMERIC(24, 10) NOT NULL CHECK (quantity > 0),
    execution_price_usd NUMERIC(18, 6) NOT NULL,
    trigger_price_usd NUMERIC(18, 6),
    gross_amount_usd NUMERIC(18, 6) NOT NULL,
    commission_bps NUMERIC(10, 4) NOT NULL,
    commission_usd NUMERIC(18, 6) NOT NULL,
    net_total_usd NUMERIC(18, 6) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'FILLED', 'CANCELLED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trades_user_status ON trades(user_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS pending_approvals (
    id VARCHAR(128) PRIMARY KEY,
    action_type VARCHAR(48) NOT NULL,
    target_user_id VARCHAR(128) NOT NULL REFERENCES users(user_id),
    target_reference_id VARCHAR(128) NOT NULL,
    amount_usd NUMERIC(18, 6) NOT NULL,
    maker_uid VARCHAR(128) NOT NULL,
    checker_uid VARCHAR(128),
    status VARCHAR(24) NOT NULL CHECK (status IN ('pending_checker', 'approved', 'rejected')),
    CONSTRAINT chk_maker_not_checker CHECK (checker_uid IS NULL OR checker_uid <> maker_uid),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aml_alerts (
    id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES users(user_id),
    alert_type VARCHAR(48) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    amount_usd NUMERIC(18, 6) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(128) PRIMARY KEY,
    actor_uid VARCHAR(128) NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(60) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS email_jobs (
    id VARCHAR(128) PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    user_id VARCHAR(128) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    template_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(128),
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
