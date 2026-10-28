import { createContext, useContext, useEffect, useMemo, useState } from 'react';

const CartContext = createContext(null);
const CART_STORAGE_KEY = 'pet-planet-cart';

export const CartProvider = ({ children }) => {
  const [cartItems, setCartItems] = useState(() => {
    const saved = localStorage.getItem(CART_STORAGE_KEY);
    return saved ? JSON.parse(saved) : [];
  });

  useEffect(() => {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cartItems));
  }, [cartItems]);

  const addToCart = (pet) => {
    setCartItems((currentItems) => {
      if (currentItems.some((item) => item.id === pet.id)) {
        return currentItems;
      }

      return [
        ...currentItems,
        {
          id: pet.id,
          name: pet.name,
          species: pet.species,
          price: pet.price,
          image: pet.image || pet.imageUrl || getFallbackPetImage(pet),
          availabilityStatus: pet.availabilityStatus,
        },
      ];
    });
  };

  const removeFromCart = (petId) => {
    setCartItems((currentItems) => currentItems.filter((item) => item.id !== petId));
  };

  const clearCart = () => {
    setCartItems([]);
  };

  const subtotal = useMemo(
    () => cartItems.reduce((total, item) => total + Number(item.price || 0), 0),
    [cartItems]
  );

  const value = useMemo(
    () => ({
      cartItems,
      cartCount: cartItems.length,
      subtotal,
      addToCart,
      removeFromCart,
      clearCart,
    }),
    [cartItems, subtotal]
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
};

const getFallbackPetImage = (pet) => {
  const species = pet.species || 'dog';
  const normalized = species.toLowerCase();
  const fallback = normalized === 'cat'
    ? 'https://images.unsplash.com/photo-1511044568932-338cba0ad803?auto=format&fit=crop&w=900&q=80'
    : 'https://images.unsplash.com/photo-1517849845537-4d257902454a?auto=format&fit=crop&w=900&q=80';

  return fallback;
};

export const useCart = () => {
  const context = useContext(CartContext);

  if (!context) {
    throw new Error('useCart must be used inside CartProvider');
  }

  return context;
};
