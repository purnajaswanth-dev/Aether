-- V2: Immutability triggers on artifact_versions and timeline_events

CREATE OR REPLACE FUNCTION reject_update_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table % is immutable. Direct UPDATE and DELETE are strictly forbidden by platform policy.', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- Trigger on artifact_versions
CREATE TRIGGER trg_immutable_artifact_versions
BEFORE UPDATE OR DELETE ON artifact_versions
FOR EACH ROW EXECUTE FUNCTION reject_update_delete();

-- Trigger on timeline_events
CREATE TRIGGER trg_immutable_timeline_events
BEFORE UPDATE OR DELETE ON timeline_events
FOR EACH ROW EXECUTE FUNCTION reject_update_delete();
