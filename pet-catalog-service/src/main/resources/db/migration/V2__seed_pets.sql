INSERT INTO pets (id, name, species, breed, age, gender, price, description, availability_status, created_at, updated_at, version)
VALUES
    ('11111111-1111-1111-1111-111111111101', 'Bella', 'DOG', 'Golden Retriever', 2, 'FEMALE', 850.00, 'Friendly and active companion who loves outdoor adventures and swimming.', 'AVAILABLE', NOW(), NOW(), 0),
    ('11111111-1111-1111-1111-111111111102', 'Max', 'DOG', 'German Shepherd', 3, 'MALE', 950.00, 'Loyal, intelligent protector and highly trainable working partner.', 'AVAILABLE', NOW(), NOW(), 0),
    ('11111111-1111-1111-1111-111111111103', 'Charlie', 'DOG', 'French Bulldog', 1, 'MALE', 1200.00, 'Playful and affectionate apartment-friendly pup with a gentle temperament.', 'AVAILABLE', NOW(), NOW(), 0),
    ('11111111-1111-1111-1111-111111111104', 'Luna', 'CAT', 'Siamese', 2, 'FEMALE', 600.00, 'Curious, social, and vocal companion who enjoys human affection and playtime.', 'AVAILABLE', NOW(), NOW(), 0),
    ('11111111-1111-1111-1111-111111111105', 'Oliver', 'CAT', 'Maine Coon', 1, 'MALE', 750.00, 'Gentle giant with a fluffy coat and sweet, easygoing personality.', 'AVAILABLE', NOW(), NOW(), 0),
    ('11111111-1111-1111-1111-111111111106', 'Milo', 'CAT', 'British Shorthair', 2, 'MALE', 700.00, 'Calm, dignified, and loving indoor pet with an plush silver coat.', 'AVAILABLE', NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

