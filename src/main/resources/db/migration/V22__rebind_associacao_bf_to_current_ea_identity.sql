-- Associação BF was recreated by EA with a new club identity for FC27.
-- Rebind the durable application identity without dropping history or the
-- encrypted Discord webhook reference. The game_version column keeps the
-- former FC26 rows distinguishable from the new FC27 acquisition stream.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM monitored_clubs WHERE club_id = '1104972') THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM monitored_clubs WHERE club_id = '457048') THEN
        RAISE EXCEPTION 'Cannot rebind Associação BF: destination club_id 457048 already exists';
    END IF;

    -- The child table has an immediate composite foreign key, so suspend it
    -- only for this atomic identity move and recreate it before commit.
    ALTER TABLE player_match_stats DROP CONSTRAINT IF EXISTS player_match_stats_canonical_match_fkey;

    UPDATE player_match_stats SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE canonical_matches SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE discord_publication_state SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE synchronization_gaps SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE explorer_observations SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE explorer_controlled_observations SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE match_editorial_presentations SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE editorial_panoramas SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE discord_webhook_secrets SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE operational_events SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE admin_audit_log SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE trial_requests SET club_id = '457048' WHERE club_id = '1104972';
    UPDATE monitored_clubs SET club_id = '457048' WHERE club_id = '1104972';

    ALTER TABLE player_match_stats
        ADD CONSTRAINT player_match_stats_canonical_match_fkey
        FOREIGN KEY (game_version, club_id, match_id)
        REFERENCES canonical_matches (game_version, club_id, match_id)
        ON DELETE CASCADE;
END $$;
