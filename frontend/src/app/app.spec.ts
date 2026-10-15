import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { AuthService } from './core/auth.service';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('shows only the brand while logged out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('.brand')?.textContent).toContain('Atlas');
    expect(root.querySelector('nav')).toBeNull();
  });

  it('shows navigation and the user when logged in', async () => {
    const login = TestBed.inject(AuthService).login('ana@exemplo.com', 'senha-forte-123');
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login')
      .flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900 });
    await login;

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('nav')?.textContent).toContain('Minhas reservas');
    expect(root.textContent).toContain('ana@exemplo.com');
  });
});
