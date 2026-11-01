-- V10: Allow administrative project purge under explicit transaction-scoped session variable
CREATE OR REPLACE FUNCTION reject_update_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('aether.allow_project_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Table % is immutable. Direct UPDATE and DELETE are strictly forbidden by platform policy.', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

