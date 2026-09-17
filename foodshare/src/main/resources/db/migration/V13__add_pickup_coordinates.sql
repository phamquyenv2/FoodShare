ALTER TABLE food_posts
    ADD COLUMN pickup_latitude DECIMAL(10, 7) NULL,
    ADD COLUMN pickup_longitude DECIMAL(10, 7) NULL;

UPDATE food_posts fp
JOIN business_profiles bp ON bp.id = fp.business_profile_id
JOIN users u ON u.id = bp.user_id
SET fp.pickup_latitude = u.latitude,
    fp.pickup_longitude = u.longitude
WHERE fp.pickup_latitude IS NULL AND fp.pickup_longitude IS NULL;
