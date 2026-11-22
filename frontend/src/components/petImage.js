// The catalog has no image field, so the storefront picks a stock photo per pet: by breed when one fits, otherwise
// from a species pool, stable per pet id. Every photo id was checked to load and to show the right animal.
const photo = (id) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=900&q=80`;

const BY_BREED = {
  'golden retriever': '1558788353-f76d92427f16',
  'german shepherd': '1589941013453-ec89f33b5e95',
  'french bulldog': '1583511655857-d19b40a7a54e',
  beagle: '1543466835-00a7907e9de1',
  boxer: '1518717758536-85ae29035b6d',
  poodle: '1552053831-71594a27632d',
  'cavalier king charles spaniel': '1530281700549-e82e7bf110d6',
  siamese: '1495360010541-f48722b34f7d',
  'maine coon': '1573865526739-10659fec78a5',
  'british shorthair': '1533738363-b7f9aef128ce',
  persian: '1606214174585-fe31582dc6ee',
  bengal: '1518791841217-8f162f1e1131',
  ragdoll: '1526336024174-e58f5cdd8e13',
  'scottish fold': '1574158622682-e40e69881006',
};

const POOLS = {
  DOG: [
    '1561037404-61cd46aa615b',
    '1537151625747-768eb6cf92b2',
    '1587300003388-59208cc962cb',
    '1477884213360-7e9d7dcc1e48',
    '1552053831-71594a27632d',
    '1543466835-00a7907e9de1',
    '1518717758536-85ae29035b6d',
    '1530281700549-e82e7bf110d6',
  ],
  CAT: [
    '1514888286974-6c03e2ca1dba',
    '1511044568932-338cba0ad803',
    '1592194996308-7b43878e84a6',
    '1543852786-1cf6624b9987',
    '1596854407944-bf87f6fdd49e',
    '1519052537078-e6302a4968d4',
    '1561948955-570b270e7c36',
    '1573865526739-10659fec78a5',
  ],
};

// FNV-1a: spreads ids that differ only in their last character (like the seeded ones) across the pool.
const hash = (text) => {
  let h = 0x811c9dc5;
  for (const c of String(text)) h = Math.imul(h ^ c.charCodeAt(0), 0x01000193) >>> 0;
  return h;
};

/** Photo URL for a pet: its own `image` if the API ever sends one, else a breed photo, else a species photo. */
export const petImage = (pet) => {
  if (pet?.image) return pet.image;
  const breed = BY_BREED[String(pet?.breed || '').trim().toLowerCase()];
  if (breed) return photo(breed);
  const pool = POOLS[pet?.species] || POOLS.DOG;
  return photo(pool[hash(pet?.id ?? pet?.name ?? '') % pool.length]);
};
