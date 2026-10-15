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
  });

  afterEach(() => http.verify());

  async function login(expiresIn = 3600): Promise<void> {
    const done = auth.login('Ana@Exemplo.com', 'senha-forte-123');
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ accessToken: 'jwt-token', tokenType: 'Bearer', expiresIn });
    await done;
  }

  it('keeps the session and sends the bearer token to the API', async () => {
    await login();

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.email()).toBe('ana@exemplo.com');
    expect(JSON.parse(sessionStorage.getItem('atlas.session')!).token).toBe('jwt-token');

    const call = firstValueFrom(TestBed.inject(HttpClient).get('/api/bookings'));
    const request = http.expectOne('/api/bookings');
    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    request.flush([]);
    await call;
  });

  it('does not send the token anywhere but the API', async () => {
    await login();

    void firstValueFrom(TestBed.inject(HttpClient).get('https://example.com/data'));
    const request = http.expectOne('https://example.com/data');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('logs out and goes to login when the API answers 401', async () => {
    await login();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const call = firstValueFrom(TestBed.inject(HttpClient).get('/api/bookings')).catch((e: unknown) => e);
    http.expectOne('/api/bookings').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    await call;

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('atlas.session')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('treats an expired token as logged out', async () => {
    await login(10); // expira em 10s, menos a margem de 30s: já nasce vencido

    expect(auth.isAuthenticated()).toBe(false);
  });
});
