async function request(path, options = {}) {
  const response = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(data?.detail || `Ошибка сервера (${response.status})`);
  }
  return data;
}

export const api = {
  resources: () => request('/resources'),
  availability: (resourceId, date) =>
    request(`/resources/${resourceId}/availability?date=${date}`),
  createBooking: (booking) =>
    request('/bookings', { method: 'POST', body: JSON.stringify(booking) }),
  bookingsByEmail: (email) =>
    request(`/bookings?email=${encodeURIComponent(email)}`),
  cancelBooking: (id, email) =>
    request(`/bookings/${id}?email=${encodeURIComponent(email)}`, { method: 'DELETE' }),
};
