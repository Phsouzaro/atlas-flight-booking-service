import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('AuthService and authInterceptor', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let client: HttpClient;

  const flushMicrotasks = () => new Promise((resolve) => setTimeout(resolve));

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    client = TestBed.inject(HttpClient);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  async function login(token = 'access-1', expiresIn = 900): Promise<void> {
    const done = auth.login('Ana@Exemplo.com', 'senha-forte-123');
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ accessToken: token, tokenType: 'Bearer', expiresIn });
    await done;
  }

  async function get(url: string): Promise<unknown> {
    const call = firstValueFrom(client.get(url)).catch((e: unknown) => e);
    await flushMicrotasks();
    return call;
  }

  it('keeps the access token only in memory and sends it to the API', async () => {
    await login();

    expect(auth.email()).toBe('ana@exemplo.com');
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);

    const call = get('/api/bookings');
    await flushMicrotasks();
    const request = http.expectOne('/api/bookings');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-1');
    request.flush([]);
    await call;
  });

  it('does not send the token anywhere but the API', async () => {
    await login();

    void get('https://example.com/data');
    await flushMicrotasks();
    const request = http.expectOne('https://example.com/data');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('renews the token before it expires, with one refresh for many requests', async () => {
    await login('access-1', 20); // vence em 20s: dentro da folga de 30s

    const first = get('/api/bookings');
    const second = get('/api/pending-actions');
    await flushMicrotasks();

    const refreshes = http.match('/api/auth/refresh');
    expect(refreshes.length).toBe(1);
    refreshes[0].flush({ accessToken: 'access-2', tokenType: 'Bearer', expiresIn: 900 });
    await flushMicrotasks();

    for (const url of ['/api/bookings', '/api/pending-actions']) {
      const request = http.expectOne(url);
      expect(request.request.headers.get('Authorization')).toBe('Bearer access-2');
      request.flush([]);
    }
    await Promise.all([first, second]);
  });

  it('refreshes once on 401 and retries the request', async () => {
    await login();

    const call = get('/api/bookings');
    await flushMicrotasks();
    http.expectOne('/api/bookings').flush({}, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();
    http.expectOne('/api/auth/refresh').flush({ accessToken: 'access-2', tokenType: 'Bearer', expiresIn: 900 });
    await flushMicrotasks();

    const retry = http.expectOne('/api/bookings');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush(['ok']);
    expect(await call).toEqual(['ok']);
  });

  it('goes back to login when the session cannot be renewed', async () => {
    await login();
    const navigate = vi.mocked(TestBed.inject(Router).navigate);

    const call = get('/api/bookings');
    await flushMicrotasks();
    http.expectOne('/api/bookings').flush({}, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();
    http.expectOne('/api/auth/refresh').flush({ reason: 'INVALID_REFRESH_TOKEN' }, { status: 401, statusText: 'Unauthorized' });
    await call;

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('retries the refresh once when another tab just rotated the cookie', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] });
    const renewed = auth.refresh();
    http.expectOne('/api/auth/refresh').flush({ reason: 'REFRESH_RACE' }, { status: 409, statusText: 'Conflict' });
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne('/api/auth/refresh').flush({ accessToken: 'access-9', tokenType: 'Bearer', expiresIn: 900 });

    expect(await renewed).toBe(true);
    expect(auth.token()).toBe('access-9');
  });

  it('restores the session from the refresh cookie when the app opens', async () => {
    const restored = auth.restore();
    http.expectOne('/api/auth/refresh').flush({ accessToken: 'access-1', tokenType: 'Bearer', expiresIn: 900 });
    await flushMicrotasks();
    const me = http.expectOne('/api/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer access-1');
    me.flush({ id: 'u1', email: 'ana@exemplo.com', role: 'USER' });
    await restored;

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.email()).toBe('ana@exemplo.com');
  });

  it('logout revokes the session on the backend', async () => {
    await login();

    const done = auth.logout();
    http.expectOne({ method: 'POST', url: '/api/auth/logout' }).flush(null, { status: 204, statusText: 'No Content' });
    await done;

    expect(auth.isAuthenticated()).toBe(false);
  });
});
