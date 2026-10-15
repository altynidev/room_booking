// Typed client for the Room Booking REST API. Mirrors the backend DTOs.

export type Role = 'USER' | 'ADMIN';
export type BookingStatus = 'ACTIVE' | 'CANCELLED';

export interface User {
  id: number;
  username: string;
  role: Role;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
}

export interface Room {
  id: number;
  name: string;
  capacity: number;
  floor: number;
}

export type RoomInput = Omit<Room, 'id'>;

export interface TimeSlot {
  start: string;
  end: string;
}

export interface Availability {
  roomId: number;
  roomName: string;
  date: string;
  bookedSlots: TimeSlot[];
  freeSlots: TimeSlot[];
}

export interface Booking {
  id: number;
  roomId: number;
  roomName: string;
  userId: number;
  username: string;
  startTime: string;
  endTime: string;
  status: BookingStatus;
}

/** Error carrying the backend's `{status, message}` body. */
export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

const TOKEN_KEY = 'roombooking.token';

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      // Storage unavailable (private mode): the session just won't survive a reload.
    }
  },
};

/** Called when an authenticated request gets 401, i.e. the token expired. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }

  if (res.status === 401 && token) onUnauthorized();
  if (!res.ok) {
    let message = res.statusText || `Request failed (${res.status})`;
    try {
      const data = await res.json();
      if (data?.message) message = data.message;
    } catch {
      // Non-JSON error body (e.g. proxy error page): keep the status text.
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  login: (username: string, password: string) =>
    request<AuthResponse>('POST', '/api/auth/login', { username, password }),
  register: (username: string, password: string) =>
    request<User>('POST', '/api/auth/register', { username, password }),
  me: () => request<User>('GET', '/api/auth/me'),

  rooms: () => request<Room[]>('GET', '/api/rooms'),
  room: (id: number) => request<Room>('GET', `/api/rooms/${id}`),
  createRoom: (room: RoomInput) => request<Room>('POST', '/api/rooms', room),
  updateRoom: (id: number, room: RoomInput) => request<Room>('PUT', `/api/rooms/${id}`, room),
  deleteRoom: (id: number) => request<void>('DELETE', `/api/rooms/${id}`),
  availability: (id: number, date: string, zone: string) =>
    request<Availability>(
      'GET',
      `/api/rooms/${id}/availability?date=${date}&zone=${encodeURIComponent(zone)}`,
    ),

  myBookings: () => request<Booking[]>('GET', '/api/bookings/my'),
  book: (roomId: number, startTime: string, endTime: string) =>
    request<Booking>('POST', '/api/bookings', { roomId, startTime, endTime }),
  cancelBooking: (id: number) => request<Booking>('PATCH', `/api/bookings/${id}/cancel`),
};
