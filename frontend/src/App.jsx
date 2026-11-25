import { useCallback, useEffect, useState } from 'react';
import { api } from './api.js';

const today = () => new Date().toLocaleDateString('sv-SE'); // ГГГГ-ММ-ДД
const formatDateTime = (iso) =>
  new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });

export default function App() {
  const [resources, setResources] = useState([]);
  const [resourceId, setResourceId] = useState(null);
  const [date, setDate] = useState(today());
  const [slots, setSlots] = useState([]);
  const [selected, setSelected] = useState([]); // индексы выбранных подряд слотов
  const [form, setForm] = useState({ name: '', email: '' });
  const [message, setMessage] = useState(null); // { type: 'ok' | 'error', text }
  const [lookupEmail, setLookupEmail] = useState('');
  const [bookings, setBookings] = useState(null);

  const resource = resources.find((r) => r.id === resourceId);

  useEffect(() => {
    api.resources()
      .then((list) => {
        setResources(list);
        if (list.length) setResourceId(list[0].id);
      })
      .catch((e) => setMessage({ type: 'error', text: e.message }));
  }, []);

  const loadSlots = useCallback(() => {
    if (!resourceId || !date) return;
    api.availability(resourceId, date)
      .then((data) => {
        setSlots(data.slots);
        setSelected([]);
      })
      .catch((e) => setMessage({ type: 'error', text: e.message }));
  }, [resourceId, date]);

  useEffect(loadSlots, [loadSlots]);

  // Клик по слоту: выбираем соседние свободные часы одним интервалом
  function toggleSlot(index) {
    setMessage(null);
    if (selected.length === 0) return setSelected([index]);
    const first = selected[0];
    const last = selected[selected.length - 1];
    if (index === first - 1) return setSelected([index, ...selected]);
    if (index === last + 1) return setSelected([...selected, index]);
    if (index === first && selected.length > 1) return setSelected(selected.slice(1));
    if (index === last) return setSelected(selected.slice(0, -1));
    setSelected([index]);
  }

  async function submit(event) {
    event.preventDefault();
    if (!selected.length) return;
    const start = slots[selected[0]].start;
    const end = slots[selected[selected.length - 1]].end;
    try {
      const booking = await api.createBooking({
        resourceId,
        customerName: form.name,
        customerEmail: form.email,
        startTime: `${date}T${start}:00`,
        endTime: `${date}T${end}:00`,
      });
      setMessage({
        type: 'ok',
        text: `Бронирование №${booking.id} подтверждено: ${booking.resourceName}, ${start}–${end}.`,
      });
      if (lookupEmail && lookupEmail === form.email) findBookings();
    } catch (e) {
      setMessage({ type: 'error', text: e.message });
    }
    loadSlots();
  }

  async function findBookings(event) {
    event?.preventDefault();
    try {
      setBookings(await api.bookingsByEmail(lookupEmail));
    } catch (e) {
      setMessage({ type: 'error', text: e.message });
    }
  }

  async function cancel(id) {
    try {
      await api.cancelBooking(id, lookupEmail);
      await findBookings();
      loadSlots();
    } catch (e) {
      setMessage({ type: 'error', text: e.message });
    }
  }

  const hours = selected.length;
  const interval = hours
    ? `${slots[selected[0]].start}–${slots[selected[hours - 1]].end}`
    : null;

  return (
    <div className="page">
      <header>
        <h1>Онлайн-бронирование</h1>
        <p>Выберите помещение, дату и свободное время</p>
      </header>

      <section>
        <h2>1. Помещение</h2>
        <div className="resources">
          {resources.map((r) => (
            <button
              key={r.id}
              type="button"
              className={`card ${r.id === resourceId ? 'active' : ''}`}
              onClick={() => setResourceId(r.id)}
            >
              <strong>{r.name}</strong>
              <span>{r.description}</span>
              <span className="meta">до {r.capacity} чел. · {r.pricePerHour} ₽/час</span>
            </button>
          ))}
        </div>
      </section>

      <section>
        <h2>2. Дата и время</h2>
        <input type="date" value={date} min={today()} onChange={(e) => setDate(e.target.value)} />
        <div className="slots">
          {slots.map((slot, i) => (
            <button
              key={slot.start}
              type="button"
              disabled={!slot.free}
              className={`slot ${selected.includes(i) ? 'selected' : ''}`}
              onClick={() => toggleSlot(i)}
            >
              {slot.start}
            </button>
          ))}
        </div>
        <p className="hint">
          Серым отмечено занятое или прошедшее время. Несколько часов подряд выбираются
          нажатием на соседние слоты.
        </p>
      </section>

      <section>
        <h2>3. Контактные данные</h2>
        <form onSubmit={submit} className="form">
          <input
            placeholder="Имя"
            required
            maxLength={120}
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <input
            type="email"
            placeholder="E-mail"
            required
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <button type="submit" className="primary" disabled={!hours}>
            {hours
              ? `Забронировать ${interval} · ${hours * (resource?.pricePerHour ?? 0)} ₽`
              : 'Выберите время'}
          </button>
        </form>
        {message && <div className={`message ${message.type}`}>{message.text}</div>}
      </section>

      <section>
        <h2>Мои бронирования</h2>
        <form onSubmit={findBookings} className="form">
          <input
            type="email"
            placeholder="E-mail, указанный при бронировании"
            required
            value={lookupEmail}
            onChange={(e) => setLookupEmail(e.target.value)}
          />
          <button type="submit">Найти</button>
        </form>
        {bookings && bookings.length === 0 && <p className="hint">Бронирований не найдено.</p>}
        {bookings && bookings.length > 0 && (
          <table>
            <thead>
              <tr><th>№</th><th>Помещение</th><th>Начало</th><th>Окончание</th><th>Статус</th><th /></tr>
            </thead>
            <tbody>
              {bookings.map((b) => (
                <tr key={b.id} className={b.status === 'CANCELLED' ? 'cancelled' : ''}>
                  <td>{b.id}</td>
                  <td>{b.resourceName}</td>
                  <td>{formatDateTime(b.startTime)}</td>
                  <td>{formatDateTime(b.endTime)}</td>
                  <td>{b.status === 'CONFIRMED' ? 'Подтверждено' : 'Отменено'}</td>
                  <td>
                    {b.status === 'CONFIRMED' && (
                      <button type="button" className="link" onClick={() => cancel(b.id)}>
                        Отменить
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
