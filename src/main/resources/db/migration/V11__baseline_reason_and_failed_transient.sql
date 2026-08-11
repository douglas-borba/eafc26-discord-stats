-- Add baseline_reason to distinguish FIRST_RUN from NO_DESTINATION baselines
ALTER TABLE discord_publication_state ADD COLUMN baseline_reason TEXT;

-- Update state comment to include FAILED_TRANSIENT
COMMENT ON COLUMN discord_publication_state.state IS
    'DELIVERING, DELIVERED, DELIVERY_UNCERTAIN, FAILED_PERMANENT, FAILED_TRANSIENT, BASELINED';
