-- One phone per collector (owner decisions 2026-11-23, increment #7, D36). The app makes a random
-- install ID once and sends it at collector-login and refresh. The first sign-in registers the
-- phone; another phone is refused until an administrator or the branch's agent releases it.
-- Written only by bulk updates (CollectorDeviceService), never by saving the staff entity.
ALTER TABLE staffs
    ADD COLUMN registered_device_id VARCHAR(100),
    ADD COLUMN device_registered_at TIMESTAMP;

-- Each session belongs to the phone it was started on: its refresh tokens work only from there.
ALTER TABLE refresh_token
    ADD COLUMN device_id VARCHAR(100);

-- Releasing the phone ends the collector's sessions.
ALTER TABLE refresh_token DROP CONSTRAINT refresh_token_revoke_reason_check;
ALTER TABLE refresh_token
    ADD CONSTRAINT refresh_token_revoke_reason_check
        CHECK (revoke_reason IN ('LOGOUT', 'REUSE_DETECTED', 'SUPERSEDED', 'DAILY_APPROVAL',
                                 'PASSWORD_RESET', 'DEACTIVATED', 'ROLES_CHANGED', 'DEVICE_RELEASED'));
